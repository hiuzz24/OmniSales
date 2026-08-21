package fu.osms.order.service.impl;

import fu.osms.audit.dto.response.AuditLogResponse;
import fu.osms.audit.entity.AuditLog;
import fu.osms.audit.mapper.AuditLogMapper;
import fu.osms.audit.repository.AuditLogRepository;
import fu.osms.audit.service.AuditService;
import fu.osms.auth.entity.User;
import fu.osms.common.dto.PageResponse;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.catalog.repository.ProductVariantRepository;
import fu.osms.inventory.entity.InventoryItem;
import fu.osms.inventory.repository.InventoryItemRepository;
import fu.osms.inventory.service.OrderStockDeliveryReadinessService;
import fu.osms.inventory.service.PlatformOrderInventoryService;
import fu.osms.sync.service.MarketplaceInventoryPropagationService;
import fu.osms.order.dto.request.CancelOrderRequest;
import fu.osms.order.dto.response.CancelReasonResponse;
import fu.osms.order.dto.response.OrderItemResponse;
import fu.osms.order.dto.response.OrderResponse;
import fu.osms.order.dto.response.OrderStats;
import fu.osms.order.entity.Order;
import fu.osms.order.entity.OrderItem;
import fu.osms.order.enums.OrderStatus;
import fu.osms.order.enums.PaymentStatus;
import fu.osms.order.enums.ShopifyCancelReason;
import fu.osms.order.mapper.OrderItemMapper;
import fu.osms.order.mapper.OrderMapper;
import fu.osms.order.repository.OrderItemRepository;
import fu.osms.order.repository.OrderRepository;
import fu.osms.order.service.OrderService;
import fu.osms.order.service.OrderStockAllocationService;
import fu.osms.order.exception.OrderMovedToWaitingStockException;
import fu.osms.order.spec.OrderSpec;
import fu.osms.sync.order.OrderStatusPushContext;
import fu.osms.sync.order.OrderStatusPushResult;
import fu.osms.sync.order.OrderStatusPushService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import fu.osms.order.event.OrderCancelledEvent;
import fu.osms.order.event.OrderPaidEvent;
import fu.osms.order.event.OrderStatusChangedEvent;
import fu.osms.order.support.OrderStockMetadata;
import fu.osms.order.support.TikTokBuyerCancellationMetadata;

import fu.osms.common.utils.SecurityUtils;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditService auditService;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final AuditLogMapper auditLogMapper;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final OrderStatusPushService orderStatusPushService;
    private final OrderStockDeliveryReadinessService orderStockDeliveryReadinessService;
    private final ApplicationEventPublisher eventPublisher;
    private final PlatformOrderInventoryService platformOrderInventoryService;
    private final OrderStockAllocationService stockAllocationService;
    private final MarketplaceInventoryPropagationService marketplaceInventoryPropagationService;

    /** Tải đơn và gom các dòng sản phẩm vào response chi tiết. */
    @Override
    @Transactional(readOnly = true)
    public OrderResponse getById(UUID id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + id));
        return responseAssembler().withItems(order);
    }

    /** Liệt kê đơn đã import bằng phân trang tại database. */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getAll(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Order> orderPage = orderRepository.findAll(pageRequest);
        return responseAssembler().page(orderPage);
    }

    /** Liệt kê đơn theo một trạng thái OSMS bằng phân trang tại database. */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getByStatus(OrderStatus status, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Order> orderPage = orderRepository.findByStatus(status, pageRequest);
        return responseAssembler().page(orderPage);
    }

    /** Kiểm tra, đẩy và lưu chuyển trạng thái đơn, sau đó phát domain event. */
    @Override
    @Transactional(noRollbackFor = OrderMovedToWaitingStockException.class)
    public OrderResponse updateStatus(UUID id, OrderStatus status) {
        Order order = orderRepository.findForUpdateById(id)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + id));

        OrderStatus oldStatus = order.getStatus();
        String oldPaymentStatus = order.getPaymentStatus();

        statusTransitionPolicy().validate(order, oldStatus, status, id);

        boolean platformConfirm = status == OrderStatus.CONFIRMED
                && statusTransitionPolicy().isPlatformOrder(order);
        Order savedOrder = platformConfirm ? stockAllocationService.confirmOrder(order.getId()) : order;
        boolean movedToWaitingStock = platformConfirm && savedOrder.getStatus() == OrderStatus.WAITING_STOCK;

        OrderStatusPushResult pushResult;
        boolean shouldAutoMarkPaid = false;
        if (movedToWaitingStock) {
            pushResult = OrderStatusPushResult.skipped("Không gửi trạng thái vì đơn chưa đủ tồn kho");
        } else {
            pushResult = orderStatusPushService.push(savedOrder, status, OrderStatusPushContext.empty());
            if (statusTransitionPolicy().shouldBlockLocalUpdate(savedOrder, status, pushResult)) {
                throw new AppException(ErrorCode.ORDER_STATUS_INVALID_TRANSITION, pushResult.getMessage());
            }

            savedOrder.setStatus(status);
            shouldAutoMarkPaid = statusTransitionPolicy().shouldAutoMarkPaid(savedOrder, status);
            if (shouldAutoMarkPaid) {
                savedOrder.setPaymentStatus("PAID");
            }
            savedOrder.setStatusChangedAt(OffsetDateTime.now());
            savedOrder = orderRepository.save(savedOrder);
        }

        var userOpt = SecurityUtils.getCurrentUser();
        UUID actorId = userOpt.map(User::getId).orElse(null);
        String actorEmail = userOpt.map(User::getEmail).orElse("system");

        boolean autoPaid = shouldAutoMarkPaid && "PAID".equals(savedOrder.getPaymentStatus()) && "UNPAID".equals(oldPaymentStatus);
        Map<String, Object> auditChanges = new java.util.HashMap<>();
        auditChanges.put("oldStatus", oldStatus.name());
        auditChanges.put("newStatus", savedOrder.getStatus().name());
        auditChanges.put("platformPushStatus", pushResult.getStatus().name());
        auditChanges.put("platformPushMessage", pushResult.getMessage());
        if (autoPaid) {
            auditChanges.put("autoPaymentStatus", "PAID");
        }
        auditService.record(actorId, actorEmail, "STATUS_CHANGE", "ORDER", id, id.toString(), auditChanges);

        if ("PAID".equals(savedOrder.getPaymentStatus()) && "UNPAID".equals(oldPaymentStatus)) {
            eventPublisher.publishEvent(new OrderPaidEvent(savedOrder));
        }
        if (oldStatus != savedOrder.getStatus()) {
            eventPublisher.publishEvent(new OrderStatusChangedEvent(
                    savedOrder.getId(), oldStatus, savedOrder.getStatus()));
        }

        if (movedToWaitingStock) {
            throw new OrderMovedToWaitingStockException();
        }

        return responseAssembler().withItems(savedOrder);
    }

    /** Xác nhận thủ công order WAITING_STOCK sau khi kiểm tra và giữ đủ toàn bộ SKU. */
    @Override
    @Transactional(noRollbackFor = OrderMovedToWaitingStockException.class)
    public OrderResponse confirmWaitingStock(UUID id) {
        Order order = orderRepository.findForUpdateById(id)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + id));
        if (order.getStatus() != OrderStatus.WAITING_STOCK) {
            throw new AppException(ErrorCode.CONFLICT, "Order không còn ở trạng thái Chờ hàng");
        }
        if (TikTokBuyerCancellationMetadata.isActive(order)) {
            throw new AppException(ErrorCode.CONFLICT, "Đơn đang có yêu cầu hủy từ khách hàng TikTok");
        }
        if (hasPlatformProgressConflict(order)) {
            throw new AppException(ErrorCode.CONFLICT, "Đơn có trạng thái trên sàn ngoài luồng OSMS, cần đối soát trước");
        }
        if (order.getPlatform() == PlatformType.TIKTOK
                && !"AWAITING_SHIPMENT".equalsIgnoreCase(rawTikTokStatus(order))) {
            throw new AppException(ErrorCode.CONFLICT,
                    "TikTok chưa ở trạng thái AWAITING_SHIPMENT để xác nhận đơn");
        }

        OrderStatus oldStatus = order.getStatus();
        Order saved = stockAllocationService.confirmOrder(id);
        Map<String, Object> auditChanges = new java.util.LinkedHashMap<>();
        auditChanges.put("oldStatus", oldStatus.name());
        auditChanges.put("newStatus", saved.getStatus().name());
        auditChanges.put("manualStockConfirmation", true);

        var userOpt = SecurityUtils.getCurrentUser();
        UUID actorId = userOpt.map(User::getId).orElse(null);
        String actorEmail = userOpt.map(User::getEmail).orElse("system");
        if (saved.getStatus() == OrderStatus.WAITING_STOCK) {
            auditChanges.put("result", "INSUFFICIENT_STOCK");
            auditService.record(actorId, actorEmail, "STOCK_CONFIRM_FAILED", "ORDER", id, id.toString(), auditChanges);
            throw new OrderMovedToWaitingStockException();
        }

        auditChanges.put("result", "RESERVED");
        auditChanges.put("items", orderItemRepository.findByOrderId(id).stream().map(item -> {
            Map<String, Object> value = new java.util.LinkedHashMap<>();
            value.put("sku", item.getSku());
            value.put("quantity", item.getQuantity());
            return value;
        }).toList());
        auditService.record(actorId, actorEmail, "STOCK_CONFIRMED_MANUALLY", "ORDER", id, id.toString(), auditChanges);
        eventPublisher.publishEvent(new OrderStatusChangedEvent(id, oldStatus, saved.getStatus()));
        return responseAssembler().withItems(saved);
    }

    /** Cập nhật trạng thái thanh toán nội bộ và dành REFUNDED cho luồng trả hàng. */
    @Override
    @Transactional
    public OrderResponse updatePaymentStatus(UUID id, PaymentStatus paymentStatus) {
        if (paymentStatus == PaymentStatus.REFUNDED) {
            throw new AppException(ErrorCode.ORDER_REFUND_SYSTEM_MANAGED);
        }
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + id));

        String oldStatus = order.getPaymentStatus();
        order.setPaymentStatus(paymentStatus.name());
        Order savedOrder = orderRepository.save(order);

        var userOpt = SecurityUtils.getCurrentUser();
        UUID actorId = userOpt.map(User::getId).orElse(null);
        String actorEmail = userOpt.map(User::getEmail).orElse("system");
        auditService.record(actorId, actorEmail, "PAYMENT_STATUS_CHANGE", "ORDER", id,
                id.toString(), java.util.Map.of("oldPaymentStatus", oldStatus, "newPaymentStatus", paymentStatus.name()));

        if ("PAID".equals(savedOrder.getPaymentStatus()) && !"PAID".equals(oldStatus)) {
            eventPublisher.publishEvent(new OrderPaidEvent(savedOrder));
        }

        return responseAssembler().withItems(savedOrder);
    }

    /** Gọi hủy trên platform và chỉ giải phóng reservation sau khi hủy được xác nhận. */
    @Override
    @Transactional
    public void cancel(UUID id, CancelOrderRequest request) {
        Order order = orderRepository.findForUpdateById(id)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + id));

        CancelOrderRequest cancelRequest = request != null ? request : new CancelOrderRequest();
        String reason = cancelRequest.getReason();
        String reasonId = cancelRequest.getReasonId();
        String tikTokReason = cancelRequest.getTikTokReason();
        ShopifyCancelReason shopifyReason = cancelRequest.getShopifyReason() != null
                ? cancelRequest.getShopifyReason()
                : ShopifyCancelReason.OTHER;
        OrderStatus oldStatus = order.getStatus();
        if (statusTransitionPolicy().requiresTextCancelReason(order) && (reason == null || reason.isBlank())) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        if (order.getPlatform() == PlatformType.LAZADA && (reasonId == null || reasonId.isBlank())) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        if (order.getPlatform() == PlatformType.TIKTOK && (tikTokReason == null || tikTokReason.isBlank())) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Vui lòng chọn lý do hủy TikTok");
        }
        if (order.getPlatform() == PlatformType.TIKTOK
                && statusTransitionPolicy().isTikTokCancellationPending(order)) {
            throw new AppException(ErrorCode.CONFLICT, "Đơn hàng đang chờ TikTok xác nhận hủy");
        }
        if (oldStatus == OrderStatus.CANCELLED) {
            throw new AppException(ErrorCode.ORDER_ALREADY_CANCELLED);
        }
        if (oldStatus != OrderStatus.PENDING
                && oldStatus != OrderStatus.CONFIRMED
                && oldStatus != OrderStatus.PROCESSING
                && oldStatus != OrderStatus.WAITING_STOCK) {
            throw new AppException(ErrorCode.ORDER_STATUS_INVALID_TRANSITION);
        }

        OrderStatusPushResult pushResult = orderStatusPushService.push(order, OrderStatus.CANCELLED,
                OrderStatusPushContext.builder()
                        .cancelReason(reason)
                        .cancelReasonId(reasonId)
                        .tikTokReason(tikTokReason)
                        .shopifyReason(shopifyReason)
                        .email(cancelRequest.getEmail())
                        .restock(cancelRequest.getRestock())
                        .refund(cancelRequest.getRefund())
                        .build());
        if (statusTransitionPolicy().shouldBlockLocalUpdate(order, OrderStatus.CANCELLED, pushResult)) {
            throw new AppException(ErrorCode.ORDER_STATUS_INVALID_TRANSITION, pushResult.getMessage());
        }

        if (order.getPlatform() == PlatformType.TIKTOK
                && Boolean.TRUE.equals(pushResult.getMetadata().get("pendingConfirmation"))) {
            order.setCancelReason(reason);
            orderRepository.save(order);

            var userOpt = SecurityUtils.getCurrentUser();
            UUID actorId = userOpt.map(User::getId).orElse(null);
            String actorEmail = userOpt.map(User::getEmail).orElse("system");
            Map<String, Object> auditChanges = new java.util.HashMap<>();
            auditChanges.put("oldStatus", oldStatus.name());
            auditChanges.put("requestedStatus", OrderStatus.CANCELLED.name());
            auditChanges.put("reason", reason != null ? reason : "");
            auditChanges.put("tikTokReason", tikTokReason);
            auditChanges.put("cancelStatus", String.valueOf(pushResult.getMetadata().get("cancelStatus")));
            auditChanges.put("stage", "REQUESTED");
            auditChanges.put("pendingConfirmation", true);
            auditChanges.put("platformPushStatus", pushResult.getStatus().name());
            auditChanges.put("platformPushMessage", pushResult.getMessage());
            auditService.record(actorId, actorEmail, "ORDER_CANCEL", "ORDER", id,
                    order.getId().toString(), auditChanges);
            return;
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelReason(reason);
        order.setStatusChangedAt(OffsetDateTime.now());
        OrderStockMetadata.clearLifecycle(order);
        Order savedOrder = orderRepository.save(order);
        if (statusTransitionPolicy().isPlatformOrder(savedOrder)) {
            platformOrderInventoryService.releaseOrderReservations(savedOrder.getId());
        } else {
            manualInventoryService().propagate(manualInventoryService().release(savedOrder));
        }

        var userOpt = SecurityUtils.getCurrentUser();
        UUID actorId = userOpt.map(User::getId).orElse(null);
        String actorEmail = userOpt.map(User::getEmail).orElse("system");
        Map<String, Object> auditChanges = new java.util.HashMap<>();
        auditChanges.put("oldStatus", oldStatus.name());
        auditChanges.put("newStatus", "CANCELLED");
        auditChanges.put("reason", reason != null ? reason : "");
        if (reasonId != null && !reasonId.isBlank()) {
            auditChanges.put("reasonId", reasonId);
        }
        if (order.getPlatform() == PlatformType.SHOPIFY) {
            auditChanges.put("shopifyReason", shopifyReason.name());
            auditChanges.put("email", cancelRequest.getEmail() == null || cancelRequest.getEmail());
            auditChanges.put("restock", cancelRequest.getRestock() == null || cancelRequest.getRestock());
            auditChanges.put("refund", cancelRequest.getRefund() == null || cancelRequest.getRefund());
        }
        auditChanges.put("platformPushStatus", pushResult.getStatus().name());
        auditChanges.put("platformPushMessage", pushResult.getMessage());
        auditService.record(actorId, actorEmail, "ORDER_CANCEL", "ORDER", id,
                order.getId().toString(), auditChanges);

        eventPublisher.publishEvent(new OrderCancelledEvent(savedOrder));
        eventPublisher.publishEvent(new OrderStatusChangedEvent(
                savedOrder.getId(), oldStatus, savedOrder.getStatus()));
    }

    /** Chuyển việc lấy lý do hủy cho adapter của platform chứa đơn. */
    @Override
    @Transactional(readOnly = true)
    public List<CancelReasonResponse> getCancelReasons(UUID id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + id));
        if (order.getStatus() == OrderStatus.CANCELLED
                || order.getStatus() == OrderStatus.IN_TRANSIT
                || order.getStatus() == OrderStatus.DELIVERED) {
            throw new AppException(ErrorCode.ORDER_STATUS_INVALID_TRANSITION);
        }
        try {
            return orderStatusPushService.getCancelReasons(order);
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException(ErrorCode.INVALID_REQUEST, e.getMessage(), e);
        }
    }

    /** Tạo trang đơn hàng đã lọc cho màn danh sách. */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getFiltered(OrderStatus status, UUID channelId, String keyword,
                                                    OffsetDateTime from, OffsetDateTime to,
                                                    UUID customerId, Boolean waitingStockExpired, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        OffsetDateTime requestNow = OffsetDateTime.now();
        Specification<Order> spec = OrderSpec.withFilters(
                status, channelId, keyword, from, to, customerId, waitingStockExpired, requestNow);
        Page<Order> orderPage = orderRepository.findAll(spec, pageRequest);
        return responseAssembler().page(orderPage);
    }

    /** Trả về các số liệu tổng hợp trên dashboard đơn hàng. */
    @Override
    @Transactional(readOnly = true)
    public OrderStats getStats() {
        return new OrderStats(
                orderRepository.countAll(),
                orderRepository.countByStatus(OrderStatus.PENDING),
                orderRepository.countByStatus(OrderStatus.WAITING_STOCK),
                orderRepository.countByStatus(OrderStatus.CONFIRMED),
                orderRepository.countByStatus(OrderStatus.PROCESSING),
                orderRepository.countByStatus(OrderStatus.SHIPPED),
                orderRepository.countByStatus(OrderStatus.DELIVERED),
                orderRepository.countByStatus(OrderStatus.CANCELLED),
                orderRepository.sumRevenueDelivered()
        );
    }

    /** Trả về audit log của đơn theo thời gian mới nhất trước. */
    @Override
    @Transactional(readOnly = true)
    public long countOrdersWithoutCustomer() {
        return orderRepository.countByCustomerIsNull();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> getOrderHistory(UUID orderId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "performedAt"));
        Page<AuditLog> logPage = auditLogRepository.findByEntityTypeAndEntityId("ORDER", orderId, pageRequest);

        List<AuditLogResponse> content = logPage.getContent().stream()
                .map(auditLogMapper::toResponse)
                .toList();

        return PageResponse.<AuditLogResponse>builder()
                .content(content)
                .page(logPage.getNumber())
                .size(logPage.getSize())
                .totalElements(logPage.getTotalElements())
                .totalPages(logPage.getTotalPages())
                .first(logPage.isFirst())
                .last(logPage.isLast())
                .build();
    }

    private ManualOrderInventoryService manualInventoryService() {
        return new ManualOrderInventoryService(
                productVariantRepository,
                inventoryItemRepository,
                orderItemRepository,
                marketplaceInventoryPropagationService
        );
    }

    private boolean hasPlatformProgressConflict(Order order) {
        if (order.getPlatformMetadata() == null) return false;
        Object raw = order.getPlatformMetadata().get("platformProgressConflict");
        return raw instanceof Map<?, ?> conflict && Boolean.TRUE.equals(conflict.get("active"));
    }

    private String rawTikTokStatus(Order order) {
        if (order.getPlatformMetadata() == null) return null;
        Object rawTikTok = order.getPlatformMetadata().get("tiktok");
        if (!(rawTikTok instanceof Map<?, ?> tikTok)) return null;
        Object rawStatus = tikTok.get("rawOrderStatus");
        return rawStatus == null ? null : String.valueOf(rawStatus);
    }

    private OrderResponseAssembler responseAssembler() {
        return new OrderResponseAssembler(orderMapper, orderItemMapper, orderItemRepository);
    }

    private OrderStatusTransitionPolicy statusTransitionPolicy() {
        return new OrderStatusTransitionPolicy(orderStockDeliveryReadinessService);
    }

}
