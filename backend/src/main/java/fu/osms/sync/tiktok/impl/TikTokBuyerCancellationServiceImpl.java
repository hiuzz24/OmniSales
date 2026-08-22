package fu.osms.sync.tiktok.impl;

import fu.osms.channel.entity.Channel;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.inventory.service.PlatformOrderInventoryService;
import fu.osms.order.dto.response.BuyerCancellationResponse;
import fu.osms.order.entity.Order;
import fu.osms.order.enums.OrderStatus;
import fu.osms.order.event.OrderCancelledEvent;
import fu.osms.order.event.OrderStatusChangedEvent;
import fu.osms.order.repository.OrderRepository;
import fu.osms.order.repository.OrderItemRepository;
import fu.osms.order.support.OrderStockMetadata;
import fu.osms.order.support.TikTokBuyerCancellationMetadata;
import fu.osms.sync.entity.WebhookEvent;
import fu.osms.sync.tiktok.TikTokBuyerCancellationService;
import fu.osms.sync.tiktok.TikTokOrderApiService;
import fu.osms.sync.service.MarketplaceInventoryPropagationService;
import fu.osms.sync.webhook.WebhookPayloadUtils;
import fu.osms.sync.webhook.impl.TikTokOrderWebhookWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TikTokBuyerCancellationServiceImpl implements TikTokBuyerCancellationService {
    private final TikTokOrderApiService apiService;
    private final TikTokOrderWebhookWriter webhookWriter;
    private final OrderRepository orderRepository;
    private final PlatformOrderInventoryService inventoryService;
    private final ApplicationEventPublisher eventPublisher;
    private final PlatformTransactionManager transactionManager;
    private final OrderItemRepository orderItemRepository;
    private final MarketplaceInventoryPropagationService inventoryPropagationService;

    @Override
    public void handleWebhook(WebhookEvent event) {
        Map<String, Object> data = WebhookPayloadUtils.copyMap(event.getRawPayload().get("data"));
        String orderId = required(data, "order_id");
        String cancelId = required(data, "cancel_id");
        TikTokOrderApiService.Cancellation searched = apiService.searchCancellation(event.getChannel(), cancelId);
        String webhookStatus = text(data.get("cancel_status"));
        String webhookRole = text(data.get("cancellations_role"));
        Long webhookUpdateTime = epoch(WebhookPayloadUtils.firstPresent(data, "update_time", "timestamp"));
        String sellerNextAction = searched.sellerNextAction();
        if (sellerNextAction == null
                && TikTokBuyerCancellationMetadata.PENDING.equalsIgnoreCase(webhookStatus)
                && "BUYER".equalsIgnoreCase(webhookRole)) {
            try {
                TikTokOrderApiService.CancellationDecisionEligibility eligibility =
                        apiService.getCancellationDecisionEligibility(event.getChannel(), cancelId);
                if (eligibility.approve().eligible() || eligibility.reject().eligible()) {
                    sellerNextAction = "SELLER_RESPOND_CANCEL";
                }
            } catch (Exception exception) {
                log.warn("Could not determine the next action for TikTok buyer cancellation {}: {}",
                        cancelId, rootMessage(exception));
            }
        }
        TikTokOrderApiService.Cancellation cancellation = new TikTokOrderApiService.Cancellation(
                cancelId,
                orderId,
                webhookStatus != null ? webhookStatus : searched.cancelStatus(),
                searched.initiatorRole() != null ? searched.initiatorRole()
                        : (TikTokBuyerCancellationMetadata.PENDING.equalsIgnoreCase(webhookStatus) ? webhookRole : null),
                webhookRole != null ? webhookRole : searched.lastActorRole(),
                sellerNextAction,
                webhookUpdateTime != null ? webhookUpdateTime : searched.updateTime());
        Map<String, Object> detail = orderRepository
                .findByChannel_IdAndExternalOrderId(event.getChannel().getId(), orderId)
                .isPresent() ? null : apiService.getOrderDetail(event.getChannel(), orderId);
        webhookWriter.writeCancellation(event.getId(), orderId, detail, cancellation);
    }

    @Override
    public BuyerCancellationResponse get(UUID orderId) {
        ActionContext context = loadContext(orderId);
        TikTokOrderApiService.CancellationDecisionEligibility eligibility = shouldLoadEligibility(context.metadata())
                ? apiService.getCancellationDecisionEligibility(context.channel(), context.cancelId())
                : unavailable("Yêu cầu hủy hiện không chờ Seller xử lý");
        return response(context.metadata(), eligibility);
    }

    @Override
    public BuyerCancellationResponse approve(UUID orderId) {
        return execute(orderId, "APPROVE", null, null);
    }

    @Override
    public BuyerCancellationResponse reject(UUID orderId, String reasonCode, String comment) {
        if (reasonCode == null || reasonCode.isBlank()) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Vui lòng chọn lý do từ chối");
        }
        return execute(orderId, "REJECT", reasonCode, comment);
    }

    private BuyerCancellationResponse execute(UUID orderId, String action, String reasonCode, String comment) {
        ActionContext loaded = loadContext(orderId);
        TikTokOrderApiService.CancellationDecisionEligibility eligibility =
                apiService.getCancellationDecisionEligibility(loaded.channel(), loaded.cancelId());
        TikTokOrderApiService.ActionDecision decision = "APPROVE".equals(action)
                ? eligibility.approve() : eligibility.reject();
        if (!decision.eligible()) {
            throw new AppException(ErrorCode.CONFLICT,
                    decision.warningMessage() == null ? "TikTok không còn cho phép thao tác này" : decision.warningMessage());
        }
        if ("REJECT".equals(action) && decision.reasons().stream()
                .noneMatch(reason -> reason.code().equals(reasonCode))) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Lý do từ chối không còn hợp lệ trên TikTok");
        }

        UUID actionRequestId = UUID.randomUUID();
        ActionContext prepared = requiredNew().execute(status -> prepare(orderId, loaded.cancelId(), action,
                actionRequestId, reasonCode));
        try {
            if ("APPROVE".equals(action)) {
                apiService.approveCancellation(prepared.channel(), prepared.cancelId(), actionRequestId.toString());
            } else {
                apiService.rejectCancellation(prepared.channel(), prepared.cancelId(), reasonCode, comment,
                        actionRequestId.toString());
            }
            requiredNew().executeWithoutResult(status -> markSubmitted(orderId, actionRequestId, action));
            return response(loadContext(orderId).metadata(), eligibility);
        } catch (Exception platformError) {
            TikTokOrderApiService.Cancellation latest;
            try {
                latest = apiService.searchCancellation(prepared.channel(), prepared.cancelId());
            } catch (Exception refreshError) {
                requiredNew().executeWithoutResult(status -> markFailed(orderId, actionRequestId, platformError));
                throw new AppException(ErrorCode.CONFLICT,
                        "Không thể xử lý yêu cầu hủy trên TikTok. Vui lòng thử lại.");
            }
            requiredNew().executeWithoutResult(status -> applyLatest(orderId, actionRequestId, latest));
            throw new AppException(ErrorCode.CONFLICT,
                    "TikTok đã thay đổi trạng thái yêu cầu hủy. Hệ thống đã tải lại dữ liệu mới nhất.");
        }
    }

    private ActionContext loadContext(UUID orderId) {
        Order order = orderRepository.findByIdWithChannel(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        if (order.getPlatform() != PlatformType.TIKTOK || order.getChannel() == null) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Đơn hàng không thuộc TikTok Shop");
        }
        requireScope(order.getChannel(), "seller.return_refund.basic");
        Map<String, Object> metadata = TikTokBuyerCancellationMetadata.value(order);
        String cancelId = text(metadata.get("cancelId"));
        if (cancelId == null || cancelId.isBlank()) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Đơn chưa có yêu cầu hủy từ khách hàng");
        }
        return new ActionContext(order.getId(), order.getChannel(), cancelId, metadata);
    }

    private ActionContext prepare(UUID orderId, String expectedCancelId, String action, UUID requestId,
                                  String reasonCode) {
        Order order = locked(orderId);
        Map<String, Object> metadata = new LinkedHashMap<>(TikTokBuyerCancellationMetadata.value(order));
        if (!expectedCancelId.equals(text(metadata.get("cancelId")))
                || !TikTokBuyerCancellationMetadata.PENDING.equalsIgnoreCase(text(metadata.get("cancelStatus")))
                || !booleanValue(metadata.get("active"))) {
            throw new AppException(ErrorCode.CONFLICT, "Yêu cầu hủy không còn chờ Seller xử lý");
        }
        if ("PROCESSING".equals(metadata.get("actionState"))) {
            throw new AppException(ErrorCode.CONFLICT, "Yêu cầu hủy đang được xử lý");
        }
        metadata.put("actionState", "PROCESSING");
        metadata.put("lastAction", action);
        metadata.put("actionRequestId", requestId.toString());
        metadata.put("lastError", null);
        if (reasonCode != null) metadata.put("rejectReason", reasonCode);
        TikTokBuyerCancellationMetadata.replace(order, metadata);
        orderRepository.save(order);
        return new ActionContext(order.getId(), order.getChannel(), expectedCancelId, metadata);
    }

    private void markSubmitted(UUID orderId, UUID requestId, String action) {
        Order order = locked(orderId);
        Map<String, Object> metadata = new LinkedHashMap<>(TikTokBuyerCancellationMetadata.value(order));
        if (!requestId.toString().equals(text(metadata.get("actionRequestId")))) return;
        metadata.put("actionState", "SUBMITTED");
        metadata.put("lastAction", action);
        metadata.put("lastError", null);
        TikTokBuyerCancellationMetadata.replace(order, metadata);
        orderRepository.save(order);
    }

    private void markFailed(UUID orderId, UUID requestId, Exception error) {
        Order order = locked(orderId);
        Map<String, Object> metadata = new LinkedHashMap<>(TikTokBuyerCancellationMetadata.value(order));
        if (!requestId.toString().equals(text(metadata.get("actionRequestId")))) return;
        metadata.put("actionState", "FAILED");
        metadata.put("lastError", rootMessage(error));
        TikTokBuyerCancellationMetadata.replace(order, metadata);
        orderRepository.save(order);
    }

    private void applyLatest(UUID orderId, UUID requestId, TikTokOrderApiService.Cancellation latest) {
        Order order = locked(orderId);
        Map<String, Object> metadata = new LinkedHashMap<>(TikTokBuyerCancellationMetadata.value(order));
        metadata.put("cancelStatus", latest.cancelStatus());
        metadata.put("lastActorRole", latest.lastActorRole());
        metadata.put("sellerNextAction", latest.sellerNextAction());
        metadata.put("lastUpdateTime", latest.updateTime());
        boolean active = TikTokBuyerCancellationMetadata.PENDING.equalsIgnoreCase(latest.cancelStatus());
        metadata.put("active", active);
        metadata.put("sellerActionRequired", active
                && TikTokBuyerCancellationMetadata.PENDING.equalsIgnoreCase(latest.cancelStatus())
                && "SELLER_RESPOND_CANCEL".equalsIgnoreCase(latest.sellerNextAction()));
        if (requestId.toString().equals(text(metadata.get("actionRequestId")))) {
            metadata.put("actionState", active ? "FAILED" : "RESOLVED");
        }
        TikTokBuyerCancellationMetadata.replace(order, metadata);
        OrderStatus previous = order.getStatus();
        if (TikTokBuyerCancellationMetadata.SUCCESS.equalsIgnoreCase(latest.cancelStatus())
                || TikTokBuyerCancellationMetadata.COMPLETE.equalsIgnoreCase(latest.cancelStatus())) {
            order.setStatus(OrderStatus.CANCELLED);
            order.setStatusChangedAt(java.time.OffsetDateTime.now());
            OrderStockMetadata.clearLifecycle(order);
        }
        Order saved = orderRepository.save(order);
        if (saved.getStatus() == OrderStatus.CANCELLED && previous != OrderStatus.CANCELLED) {
            inventoryService.releaseOrderReservations(saved.getId());
            eventPublisher.publishEvent(new OrderCancelledEvent(saved));
            eventPublisher.publishEvent(new OrderStatusChangedEvent(saved.getId(), previous, OrderStatus.CANCELLED));
        } else if (!active && saved.getStatus() == OrderStatus.WAITING_STOCK) {
            inventoryPropagationService.scheduleWaitingStockReconcile(
                    orderItemRepository.findByOrderId(saved.getId()).stream()
                            .filter(item -> item.getVariant() != null)
                            .map(item -> item.getVariant().getId())
                            .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new)));
        }
    }

    private BuyerCancellationResponse response(Map<String, Object> metadata,
                                               TikTokOrderApiService.CancellationDecisionEligibility eligibility) {
        boolean sellerActionRequired = booleanValue(metadata.get("sellerActionRequired"))
                || eligibility.approve().eligible()
                || eligibility.reject().eligible();
        return new BuyerCancellationResponse(
                text(metadata.get("cancelId")), text(metadata.get("cancelStatus")),
                booleanValue(metadata.get("active")), sellerActionRequired,
                text(metadata.get("sellerNextAction")),
                text(metadata.get("actionState")),
                new BuyerCancellationResponse.Decision(
                        eligibility.approve().eligible(), eligibility.approve().warningMessage()),
                new BuyerCancellationResponse.RejectDecision(
                        eligibility.reject().eligible(), eligibility.reject().warningMessage(),
                        localizedRejectReasons(eligibility.reject().reasons())));
    }

    private List<BuyerCancellationResponse.Reason> localizedRejectReasons(
            List<TikTokOrderApiService.DecisionReason> reasons) {
        Map<String, BuyerCancellationResponse.Reason> uniqueByLabel = new LinkedHashMap<>();
        for (TikTokOrderApiService.DecisionReason reason : reasons) {
            String label = vietnameseRejectReason(reason.code(), reason.label());
            uniqueByLabel.putIfAbsent(label, new BuyerCancellationResponse.Reason(reason.code(), label));
        }
        return List.copyOf(uniqueByLabel.values());
    }

    private String vietnameseRejectReason(String code, String platformLabel) {
        if (code == null) return platformLabel;
        return switch (code) {
            case "order_manage_list_action_respond_popup_reject_reason_invalid_cancellation_reason",
                 "reverse_reject_request_reason_1" -> "Lý do hủy của khách hàng không hợp lệ";
            case "order_manage_list_action_respond_popup_reject_reason_delivered",
                 "reverse_reject_request_reason_4" -> "Đơn hàng đang được giao đúng tiến độ";
            case "order_manage_list_action_respond_popup_reject_reason_buyer_agree",
                 "reverse_reject_request_reason_5" -> "Đã đạt thỏa thuận với khách hàng";
            case "seller_reject_apply_product_has_been_packed",
                 "ecom_reverse_reject_cancel_reason_product_packed" -> "Đơn hàng đã được đóng gói hoặc gửi đi";
            case "seller_reject_apply_unable_to_change_address" -> "Không thể thay đổi địa chỉ giao hàng";
            default -> platformLabel == null || platformLabel.isBlank() ? code : platformLabel;
        };
    }

    private TikTokOrderApiService.CancellationDecisionEligibility unavailable(String message) {
        TikTokOrderApiService.ActionDecision decision =
                new TikTokOrderApiService.ActionDecision(false, message, List.of());
        return new TikTokOrderApiService.CancellationDecisionEligibility(decision, decision);
    }

    private boolean shouldLoadEligibility(Map<String, Object> metadata) {
        return booleanValue(metadata.get("active"))
                && TikTokBuyerCancellationMetadata.PENDING.equalsIgnoreCase(text(metadata.get("cancelStatus")));
    }

    private void requireScope(Channel channel, String requiredScope) {
        Object configured = channel.getMetadata() == null ? null : channel.getMetadata().get("grantedScopes");
        if (!(configured instanceof Collection<?> scopes) || scopes.isEmpty()) return;
        if (scopes.stream().map(String::valueOf).noneMatch(requiredScope::equals)) {
            throw new AppException(ErrorCode.FORBIDDEN,
                    "Kênh TikTok thiếu quyền " + requiredScope + ". Vui lòng kết nối lại kênh.");
        }
    }

    private TransactionTemplate requiredNew() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private Order locked(UUID orderId) {
        return orderRepository.findForUpdateById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
    }

    private String required(Map<String, Object> data, String key) {
        String value = text(data.get(key));
        if (value == null || value.isBlank()) throw new IllegalArgumentException("TikTok webhook is missing data." + key);
        return value;
    }

    private String text(Object value) { return WebhookPayloadUtils.text(value); }
    private boolean booleanValue(Object value) {
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }
    private Long epoch(Object value) {
        try { return value == null ? null : Long.parseLong(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return null; }
    }
    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private record ActionContext(UUID orderId, Channel channel, String cancelId, Map<String, Object> metadata) {
    }
}
