package fu.osms.order.service.impl;

import fu.osms.audit.dto.response.AuditLogResponse;
import fu.osms.audit.entity.AuditLog;
import fu.osms.audit.mapper.AuditLogMapper;
import fu.osms.audit.repository.AuditLogRepository;
import fu.osms.audit.service.AuditService;
import fu.osms.auth.entity.User;
import fu.osms.channel.entity.Channel;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.common.dto.PageResponse;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.customer.entity.Customer;
import fu.osms.customer.repository.CustomerRepository;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.catalog.repository.ProductVariantRepository;
import fu.osms.inventory.entity.InventoryItem;
import fu.osms.inventory.repository.InventoryItemRepository;
import fu.osms.inventory.service.InventoryAlertService;
import fu.osms.sync.service.MarketplaceInventoryPropagationService;
import fu.osms.order.dto.request.CancelOrderRequest;
import fu.osms.order.dto.request.OrderItemRequest;
import fu.osms.order.dto.request.OrderRequest;
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
import fu.osms.order.event.OrderCreatedEvent;
import fu.osms.order.event.OrderCancelledEvent;
import fu.osms.order.event.OrderPaidEvent;

import fu.osms.common.utils.SecurityUtils;
import java.math.BigDecimal;
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
    private final ChannelRepository channelRepository;
    private final CustomerRepository customerRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditService auditService;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final AuditLogMapper auditLogMapper;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryAlertService inventoryAlertService;
    private final OrderStatusPushService orderStatusPushService;
    private final ApplicationEventPublisher eventPublisher;
    private final MarketplaceInventoryPropagationService marketplaceInventoryPropagationService;

    @Override
    @Transactional
    public OrderResponse create(OrderRequest request) {
        Order order = orderMapper.toEntity(request);

        if (request.getChannelId() != null) {
            Channel channel = channelRepository.findById(request.getChannelId())
                    .orElseThrow(() -> new EntityNotFoundException("Channel not found: " + request.getChannelId()));
            order.setChannel(channel);
        }

        if (request.getCustomerId() != null) {
            Customer customer = customerRepository.findById(request.getCustomerId())
                    .orElseThrow(() -> new EntityNotFoundException("Customer not found: " + request.getCustomerId()));
            order.setCustomer(customer);
        }

        order.setStatusChangedAt(OffsetDateTime.now());
        Order savedOrder = orderRepository.save(order);

        Set<UUID> changedVariantIds = new HashSet<>();
        for (OrderItemRequest itemReq : request.getItems()) {
            ProductVariant variant = resolveVariant(itemReq);
            OrderItem item = OrderItem.builder()
                    .order(savedOrder)
                    .variant(variant)
                    .sku(itemReq.getSku())
                    .name(itemReq.getName())
                    .quantity(itemReq.getQuantity())
                    .unitPrice(itemReq.getUnitPrice())
                    .discountAmount(itemReq.getDiscountAmount() != null ? itemReq.getDiscountAmount() : BigDecimal.ZERO)
                    .build();
            orderItemRepository.save(item);

            if (variant != null) {
                if (reserveInventory(variant, itemReq.getQuantity())) {
                    changedVariantIds.add(variant.getId());
                }
            }
        }
        marketplaceInventoryPropagationService.schedulePushAvailableStock(changedVariantIds);

        var userOpt = SecurityUtils.getCurrentUser();
        UUID actorId = userOpt.map(User::getId).orElse(null);
        String actorEmail = userOpt.map(User::getEmail).orElse("system");
        auditService.record(actorId, actorEmail, "CREATE", "ORDER", savedOrder.getId(),
                savedOrder.getId().toString(), java.util.Map.of("status", savedOrder.getStatus().name()));

        eventPublisher.publishEvent(new OrderCreatedEvent(savedOrder));

        return toResponseWithItems(savedOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getById(UUID id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + id));
        return toResponseWithItems(order);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getAll(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Order> orderPage = orderRepository.findAll(pageRequest);
        return toPageResponse(orderPage);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getByStatus(OrderStatus status, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Order> orderPage = orderRepository.findByStatus(status, pageRequest);
        return toPageResponse(orderPage);
    }

    @Override
    @Transactional
    public OrderResponse updateStatus(UUID id, OrderStatus status) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + id));

        OrderStatus oldStatus = order.getStatus();
        String oldPaymentStatus = order.getPaymentStatus();

        if (oldStatus == OrderStatus.CANCELLED && status == OrderStatus.CANCELLED) {
            throw new AppException(ErrorCode.ORDER_ALREADY_CANCELLED);
        }

        validateTikTokProcessingTransition(order, oldStatus, status);

        OrderStatusPushResult pushResult = orderStatusPushService.push(order, status, OrderStatusPushContext.empty());
        if (shouldBlockLocalUpdate(order, status, pushResult)) {
            throw new AppException(ErrorCode.ORDER_STATUS_INVALID_TRANSITION, pushResult.getMessage());
        }

        if (status == OrderStatus.CANCELLED) {
            order.setStatus(OrderStatus.CANCELLED);
            marketplaceInventoryPropagationService.schedulePushAvailableStock(releaseReservedInventory(order));
        } else {
            order.setStatus(status);
        }

        boolean shouldAutoMarkPaid = shouldAutoMarkPaid(order, status);
        if (shouldAutoMarkPaid) {
            order.setPaymentStatus("PAID");
        }

        order.setStatusChangedAt(OffsetDateTime.now());

        Order savedOrder = orderRepository.save(order);

        var userOpt = SecurityUtils.getCurrentUser();
        UUID actorId = userOpt.map(User::getId).orElse(null);
        String actorEmail = userOpt.map(User::getEmail).orElse("system");

        boolean autoPaid = shouldAutoMarkPaid && "PAID".equals(savedOrder.getPaymentStatus()) && "UNPAID".equals(oldPaymentStatus);
        Map<String, Object> auditChanges = new java.util.HashMap<>();
        auditChanges.put("oldStatus", oldStatus.name());
        auditChanges.put("newStatus", status.name());
        auditChanges.put("platformPushStatus", pushResult.getStatus().name());
        auditChanges.put("platformPushMessage", pushResult.getMessage());
        if (autoPaid) {
            auditChanges.put("autoPaymentStatus", "PAID");
        }
        auditService.record(actorId, actorEmail, "STATUS_CHANGE", "ORDER", id, id.toString(), auditChanges);

        if (savedOrder.getStatus() == OrderStatus.CANCELLED && oldStatus != OrderStatus.CANCELLED) {
            eventPublisher.publishEvent(new OrderCancelledEvent(savedOrder));
        }
        if ("PAID".equals(savedOrder.getPaymentStatus()) && "UNPAID".equals(oldPaymentStatus)) {
            eventPublisher.publishEvent(new OrderPaidEvent(savedOrder));
        }

        return toResponseWithItems(savedOrder);
    }

    @Override
    @Transactional
    public OrderResponse updatePaymentStatus(UUID id, PaymentStatus paymentStatus) {
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

        return toResponseWithItems(savedOrder);
    }

    @Override
    @Transactional
    public OrderResponse update(UUID id, OrderRequest request) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + id));

        orderMapper.updateEntityFromRequest(request, order);

        if (request.getChannelId() != null) {
            Channel channel = channelRepository.findById(request.getChannelId())
                    .orElseThrow(() -> new EntityNotFoundException("Channel not found: " + request.getChannelId()));
            order.setChannel(channel);
        }

        if (request.getCustomerId() != null) {
            Customer customer = customerRepository.findById(request.getCustomerId())
                    .orElseThrow(() -> new EntityNotFoundException("Customer not found: " + request.getCustomerId()));
            order.setCustomer(customer);
        }

        Order savedOrder = orderRepository.save(order);

        var userOpt = SecurityUtils.getCurrentUser();
        UUID actorId = userOpt.map(User::getId).orElse(null);
        String actorEmail = userOpt.map(User::getEmail).orElse("system");
        auditService.record(actorId, actorEmail, "UPDATE", "ORDER", id,
                savedOrder.getId().toString(), null);

        return toResponseWithItems(savedOrder);
    }

    @Override
    @Transactional
    public void cancel(UUID id, CancelOrderRequest request) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + id));

        CancelOrderRequest cancelRequest = request != null ? request : new CancelOrderRequest();
        String reason = cancelRequest.getReason();
        String reasonId = cancelRequest.getReasonId();
        String tikTokReason = cancelRequest.getTikTokReason();
        ShopifyCancelReason shopifyReason = cancelRequest.getShopifyReason() != null
                ? cancelRequest.getShopifyReason()
                : ShopifyCancelReason.OTHER;
        OrderStatus oldStatus = order.getStatus();
        if (requiresTextCancelReason(order) && (reason == null || reason.isBlank())) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        if (order.getPlatform() == PlatformType.LAZADA && (reasonId == null || reasonId.isBlank())) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        if (order.getPlatform() == PlatformType.TIKTOK && (tikTokReason == null || tikTokReason.isBlank())) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Vui lòng chọn lý do hủy TikTok");
        }
        if (order.getPlatform() == PlatformType.TIKTOK && isTikTokCancellationPending(order)) {
            throw new AppException(ErrorCode.CONFLICT, "Đơn hàng đang chờ TikTok xác nhận hủy");
        }
        if (oldStatus == OrderStatus.CANCELLED) {
            throw new AppException(ErrorCode.ORDER_ALREADY_CANCELLED);
        }
        if (oldStatus == OrderStatus.IN_TRANSIT || oldStatus == OrderStatus.DELIVERED) {
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
        if (shouldBlockLocalUpdate(order, OrderStatus.CANCELLED, pushResult)) {
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

        Set<UUID> changedVariantIds = releaseReservedInventory(order);
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelReason(reason);
        order.setStatusChangedAt(OffsetDateTime.now());
        Order savedOrder = orderRepository.save(order);

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

        marketplaceInventoryPropagationService.schedulePushAvailableStock(changedVariantIds);
        eventPublisher.publishEvent(new OrderCancelledEvent(savedOrder));
    }

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

    private boolean isPlatformOrder(Order order) {
        return order.getChannel() != null
                && order.getPlatform() != null
                && order.getPlatform() != PlatformType.MANUAL;
    }

    private boolean shouldAutoMarkPaid(Order order, OrderStatus status) {
        return !isPlatformOrder(order)
                && status == OrderStatus.DELIVERED
                && "UNPAID".equals(order.getPaymentStatus());
    }

    private boolean isStrictPlatformOrder(Order order) {
        return order.getPlatform() == PlatformType.LAZADA
                || order.getPlatform() == PlatformType.SHOPIFY
                || order.getPlatform() == PlatformType.TIKTOK;
    }

    private boolean requiresTextCancelReason(Order order) {
        return order.getPlatform() != PlatformType.LAZADA
                && order.getPlatform() != PlatformType.SHOPIFY
                && order.getPlatform() != PlatformType.TIKTOK;
    }

    private boolean shouldBlockLocalUpdate(Order order, OrderStatus status, OrderStatusPushResult pushResult) {
        if (!isPlatformOrder(order) || !requiresPlatformPush(order, status)) {
            return false;
        }
        if (isStrictPlatformOrder(order)) {
            return !pushResult.isSuccess();
        }
        return !pushResult.isSuccess() && !pushResult.isSkipped();
    }

    private boolean requiresPlatformPush(Order order, OrderStatus status) {
        if (order.getPlatform() == PlatformType.LAZADA) {
            return status == OrderStatus.PROCESSING
                    || status == OrderStatus.SHIPPED
                    || status == OrderStatus.CANCELLED;
        }
        if (order.getPlatform() == PlatformType.SHOPIFY) {
            return status == OrderStatus.SHIPPED
                    || status == OrderStatus.CANCELLED;
        }
        if (order.getPlatform() == PlatformType.TIKTOK) {
            return status == OrderStatus.SHIPPED
                    || status == OrderStatus.CANCELLED;
        }
        return status == OrderStatus.CANCELLED;
    }

    private boolean isTikTokCancellationPending(Order order) {
        if (order.getPlatformMetadata() == null) {
            return false;
        }
        Object rawTikTok = order.getPlatformMetadata().get("tiktok");
        if (!(rawTikTok instanceof Map<?, ?> tikTok)) {
            return false;
        }
        Object pending = tikTok.get("pendingConfirmation");
        return pending instanceof Boolean value ? value : Boolean.parseBoolean(String.valueOf(pending));
    }

    private void validateTikTokProcessingTransition(Order order, OrderStatus oldStatus, OrderStatus targetStatus) {
        if (order.getPlatform() != PlatformType.TIKTOK || oldStatus != OrderStatus.PENDING) {
            return;
        }
        if (targetStatus != OrderStatus.CONFIRMED
                && targetStatus != OrderStatus.PROCESSING
                && targetStatus != OrderStatus.SHIPPED) {
            return;
        }
        String rawStatus = tikTokRawOrderStatus(order);
        if (!"AWAITING_SHIPMENT".equalsIgnoreCase(rawStatus)) {
            throw new AppException(
                    ErrorCode.ORDER_STATUS_INVALID_TRANSITION,
                    "TikTok chưa chuyển đơn sang AWAITING_SHIPMENT; trạng thái hiện tại="
                            + (rawStatus != null ? rawStatus : "UNKNOWN")
            );
        }
    }

    private String tikTokRawOrderStatus(Order order) {
        if (order.getPlatformMetadata() == null) {
            return null;
        }
        Object rawTikTok = order.getPlatformMetadata().get("tiktok");
        if (!(rawTikTok instanceof Map<?, ?> tikTok)) {
            return null;
        }
        Object rawStatus = tikTok.get("rawOrderStatus");
        return rawStatus != null ? String.valueOf(rawStatus) : null;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getFiltered(OrderStatus status, UUID channelId, String keyword,
                                                    OffsetDateTime from, OffsetDateTime to,
                                                    int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        Specification<Order> spec = OrderSpec.withFilters(status, channelId, keyword, from, to);
        Page<Order> orderPage = orderRepository.findAll(spec, pageRequest);
        return toPageResponse(orderPage);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderStats getStats() {
        return new OrderStats(
                orderRepository.countAll(),
                orderRepository.countByStatus(OrderStatus.PENDING),
                orderRepository.countByStatus(OrderStatus.CONFIRMED),
                orderRepository.countByStatus(OrderStatus.PROCESSING),
                orderRepository.countByStatus(OrderStatus.SHIPPED),
                orderRepository.countByStatus(OrderStatus.DELIVERED),
                orderRepository.countByStatus(OrderStatus.CANCELLED),
                orderRepository.sumRevenueDelivered()
        );
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

    private OrderResponse toResponseWithItems(Order order) {
        OrderResponse response = orderMapper.toResponseWithItems(order);

        List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
        List<OrderItemResponse> itemResponses = items.stream()
                .map(orderItemMapper::toResponse)
                .toList();
        response.setItems(itemResponses);

        return response;
    }

    private PageResponse<OrderResponse> toPageResponse(Page<Order> orderPage) {
        List<OrderResponse> content = orderPage.getContent().stream()
                .map(orderMapper::toResponse)
                .toList();
        attachItems(content);

        return PageResponse.<OrderResponse>builder()
                .content(content)
                .page(orderPage.getNumber())
                .size(orderPage.getSize())
                .totalElements(orderPage.getTotalElements())
                .totalPages(orderPage.getTotalPages())
                .first(orderPage.isFirst())
                .last(orderPage.isLast())
                .build();
    }

    private void attachItems(List<OrderResponse> orders) {
        if (orders == null || orders.isEmpty()) {
            return;
        }

        List<UUID> orderIds = orders.stream()
                .map(OrderResponse::getId)
                .toList();
        Map<UUID, List<OrderItemResponse>> itemsByOrderId = orderItemRepository.findByOrderIdIn(orderIds).stream()
                .collect(Collectors.groupingBy(
                        item -> item.getOrder().getId(),
                        Collectors.mapping(orderItemMapper::toResponse, Collectors.toList())
                ));

        orders.forEach(order ->
                order.setItems(itemsByOrderId.getOrDefault(order.getId(), List.of())));
    }

    private ProductVariant resolveVariant(OrderItemRequest itemReq) {
        if (itemReq.getVariantId() != null) {
            return productVariantRepository.findById(itemReq.getVariantId())
                    .orElseThrow(() -> new AppException(ErrorCode.VARIANT_NOT_FOUND));
        }
        if (itemReq.getSku() == null || itemReq.getSku().isBlank()) {
            return null;
        }
        return productVariantRepository.findBySkuAndDeletedAtIsNull(itemReq.getSku()).orElse(null);
    }

    private boolean reserveInventory(ProductVariant variant, int quantity) {
        List<InventoryItem> inventoryItems = inventoryItemRepository.findByVariantIdWithLock(variant.getId());
        if (inventoryItems.isEmpty()) {
            throw new AppException(ErrorCode.INVENTORY_ITEM_NOT_FOUND,
                    "SKU " + variant.getSku() + " is not available in any warehouse.");
        }

        int totalAvailable = inventoryItems.stream().mapToInt(this::availableQuantity).sum();
        if (totalAvailable < quantity) {
            throw new AppException(ErrorCode.INSUFFICIENT_STOCK,
                    "SKU " + variant.getSku() + " has only " + totalAvailable + " available units.");
        }

        int remaining = quantity;
        List<InventoryItem> changedItems = new ArrayList<>();
        for (InventoryItem item : inventoryItems) {
            if (remaining <= 0) break;
            int reserveFromItem = Math.min(availableQuantity(item), remaining);
            if (reserveFromItem <= 0) continue;

            item.setReservedQuantity(safeInt(item.getReservedQuantity()) + reserveFromItem);
            changedItems.add(item);
            remaining -= reserveFromItem;
        }

        inventoryItemRepository.saveAll(changedItems);
        changedItems.forEach(inventoryAlertService::notifyLowStockAfterStockChange);
        return !changedItems.isEmpty();
    }

    private Set<UUID> releaseReservedInventory(Order order) {
        Set<UUID> changedVariantIds = new HashSet<>();
        List<OrderItem> orderItems = orderItemRepository.findByOrderId(order.getId());
        for (OrderItem orderItem : orderItems) {
            ProductVariant variant = orderItem.getVariant();
            if (variant == null) {
                variant = resolveVariantBySku(orderItem.getSku());
            }
            if (variant == null) continue;

            int remaining = safeInt(orderItem.getQuantity());
            List<InventoryItem> inventoryItems = inventoryItemRepository.findByVariantIdWithLock(variant.getId());
            List<InventoryItem> changedItems = new ArrayList<>();

            for (InventoryItem item : inventoryItems) {
                if (remaining <= 0) break;
                int releaseFromItem = Math.min(safeInt(item.getReservedQuantity()), remaining);
                if (releaseFromItem <= 0) continue;

                item.setReservedQuantity(safeInt(item.getReservedQuantity()) - releaseFromItem);
                changedItems.add(item);
                remaining -= releaseFromItem;
            }

            if (!changedItems.isEmpty()) {
                inventoryItemRepository.saveAll(changedItems);
                changedVariantIds.add(variant.getId());
            }
        }
        return changedVariantIds;
    }

    private ProductVariant resolveVariantBySku(String sku) {
        if (sku == null || sku.isBlank()) {
            return null;
        }
        return productVariantRepository.findBySkuAndDeletedAtIsNull(sku).orElse(null);
    }

    private int availableQuantity(InventoryItem item) {
        return safeInt(item.getQuantityOnHand()) - safeInt(item.getReservedQuantity());
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

}
