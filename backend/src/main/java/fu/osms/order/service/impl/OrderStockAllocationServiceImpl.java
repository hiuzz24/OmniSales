package fu.osms.order.service.impl;

import fu.osms.catalog.entity.ProductVariant;
import fu.osms.catalog.repository.ProductVariantRepository;
import fu.osms.common.enums.PlatformType;
import fu.osms.inventory.dto.response.ReservationOutcome;
import fu.osms.inventory.entity.InventoryItem;
import fu.osms.inventory.enums.ReservationResult;
import fu.osms.inventory.repository.InventoryItemRepository;
import fu.osms.inventory.service.PlatformOrderInventoryService;
import fu.osms.order.dto.response.WaitingStockItemResponse;
import fu.osms.order.entity.Order;
import fu.osms.order.entity.OrderItem;
import fu.osms.order.enums.OrderStatus;
import fu.osms.order.event.OrderStatusChangedEvent;
import fu.osms.order.support.TikTokBuyerCancellationMetadata;
import fu.osms.order.repository.OrderItemRepository;
import fu.osms.order.repository.OrderRepository;
import fu.osms.order.service.OrderStockAllocationService;
import fu.osms.notification.service.OrderWorkflowNotificationService;
import fu.osms.sync.service.MarketplaceWarehouseConsistencyService;
import fu.osms.sync.service.MarketplaceInventoryPropagationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderStockAllocationServiceImpl implements OrderStockAllocationService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductVariantRepository variantRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final MarketplaceWarehouseConsistencyService warehouseConsistencyService;
    private final MarketplaceInventoryPropagationService inventoryPropagationService;
    private final PlatformOrderInventoryService inventoryService;
    private final OrderWorkflowNotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${osms.order.waiting-stock-timeout-hours:48}")
    private long waitingTimeoutHours;

    /** Phân loại order sau webhook/manual pull bằng cùng một quy tắc tồn kho. */
    @Override
    @Transactional
    public Order classifyAfterImport(UUID orderId) {
        Order order = locked(orderId);
        if (TikTokBuyerCancellationMetadata.isActive(order)) return order;
        if (order.getStatus() == OrderStatus.CANCELLED) {
            inventoryService.releaseOrderReservations(orderId);
            clearAllocation(order);
            return orderRepository.save(order);
        }
        if (order.getStatus() == OrderStatus.WAITING_STOCK) return order;

        if (order.getPlatform() == PlatformType.TIKTOK && order.getStatus() == OrderStatus.CONFIRMED) {
            return applyHardReservation(order, false);
        }
        return order;
    }

    @Override
    @Transactional
    public Order confirmOrder(UUID orderId) {
        Order order = locked(orderId);
        List<Requirement> requirements = requirements(order);
        Set<UUID> variantIds = requirements.stream()
                .map(Requirement::variant)
                .filter(java.util.Objects::nonNull)
                .map(ProductVariant::getId)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        if ((order.getPlatform() == PlatformType.SHOPIFY || order.getPlatform() == PlatformType.LAZADA)
                && requirements.stream().noneMatch(value -> value.variant() == null)) {
            List<WaitingStockItemResponse> unavailable = unavailableForConfirm(requirements);
            if (!unavailable.isEmpty()) {
                Order result = moveToWaiting(order, unavailable, "INSUFFICIENT_STOCK", true);
                inventoryPropagationService.scheduleWaitingStockReconcile(variantIds);
                return result;
            }
        }
        Order result = applyHardReservation(order, false);
        if ((order.getPlatform() == PlatformType.SHOPIFY || order.getPlatform() == PlatformType.LAZADA)
                && result.getStatus() == OrderStatus.WAITING_STOCK) {
            inventoryPropagationService.scheduleWaitingStockReconcile(variantIds);
        }
        return result;
    }

    /** Đưa order Shopify/Lazada PENDING sang hàng chờ khi tồn thật không còn đủ sau một lần Confirm. */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Order movePendingOrderToWaitingIfUnavailable(UUID orderId) {
        Order order = locked(orderId);
        if (order.getStatus() != OrderStatus.PENDING
                || (order.getPlatform() != PlatformType.SHOPIFY && order.getPlatform() != PlatformType.LAZADA)) {
            return order;
        }

        List<Requirement> requirements = requirements(order);
        if (requirements.stream().anyMatch(value -> value.variant() == null)) {
            OrderStatus before = order.getStatus();
            Order saved = moveToWaiting(order, missingForInvalid(requirements), "VARIANT_MAPPING_MISSING", false);
            eventPublisher.publishEvent(new OrderStatusChangedEvent(saved.getId(), before, saved.getStatus()));
            return saved;
        }

        UUID warehouseId = warehouseConsistencyService.resolveMasterWarehouse().getId();
        List<UUID> variantIds = requirements.stream().map(value -> value.variant().getId()).distinct()
                .sorted(Comparator.comparing(UUID::toString)).toList();
        Map<UUID, InventoryItem> byVariant = new HashMap<>();
        inventoryItemRepository.findByWarehouseIdAndVariantIdInWithLock(warehouseId, variantIds)
                .forEach(value -> byVariant.put(value.getVariant().getId(), value));

        List<WaitingStockItemResponse> missing = unavailableItems(requirements, byVariant);
        if (missing.isEmpty()) return order;

        OrderStatus before = order.getStatus();
        Order saved = moveToWaiting(order, missing, "INSUFFICIENT_STOCK", true);
        eventPublisher.publishEvent(new OrderStatusChangedEvent(saved.getId(), before, saved.getStatus()));
        return saved;
    }

    /** Thử cấp lại hàng cho đúng một order; mỗi lần gọi là transaction độc lập. */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Order reconcileWaitingOrder(UUID orderId) {
        Order order = locked(orderId);
        if (TikTokBuyerCancellationMetadata.isActive(order)
                || order.getStatus() != OrderStatus.WAITING_STOCK || isExpired(order)
                || hasPlatformConflict(order) || !isFifoEligible(order)) return order;
        OrderStatus before = order.getStatus();
        Order result = order.getPlatform() == PlatformType.TIKTOK
                ? applyHardReservation(order, true)
                : promoteWaitingOrder(order, true);
        if (before != result.getStatus()) {
            eventPublisher.publishEvent(new OrderStatusChangedEvent(result.getId(), before, result.getStatus()));
        }
        return result;
    }

    private Order applyHardReservation(Order order, boolean fromFifo) {
        OffsetDateTime now = OffsetDateTime.now();
        if (order.getWaitingStockExpiresAt() != null && !order.getWaitingStockExpiresAt().isAfter(now)) {
            return moveToWaiting(order, List.of(), "ALLOCATION_EXPIRED", false);
        }
        ReservationOutcome outcome = inventoryService.tryReserve(order.getId());
        if (outcome.result() == ReservationResult.RESERVED
                || outcome.result() == ReservationResult.ALREADY_RESERVED) {
            order.setStatus(OrderStatus.CONFIRMED);
            order.setStatusChangedAt(now);
            clearAllocation(order);
            Order saved = orderRepository.save(order);
            if (fromFifo) notifyAfterCommit(saved, "ORDER_STOCK_OFFER", "Đã cấp tồn kho cho đơn TikTok",
                    "Đơn đã được giữ đủ tồn kho và chuyển sang Đã xác nhận.", false);
            return saved;
        }
        boolean fifoEligible = outcome.result() == ReservationResult.INSUFFICIENT_STOCK;
        return moveToWaiting(order, outcome.missingItems(), outcome.result().name(), fifoEligible);
    }

    /** Đưa order Shopify/Lazada đủ tồn từ WAITING_STOCK về PENDING để Sales xác nhận. */
    private Order promoteWaitingOrder(Order order, boolean fromFifo) {
        OffsetDateTime now = OffsetDateTime.now();
        List<Requirement> requirements = requirements(order);
        if (requirements.stream().anyMatch(value -> value.variant() == null)) {
            return moveToWaiting(order, missingForInvalid(requirements), "VARIANT_MAPPING_MISSING", false);
        }
        UUID warehouseId = warehouseConsistencyService.resolveMasterWarehouse().getId();
        List<UUID> variantIds = requirements.stream().map(value -> value.variant().getId()).distinct()
                .sorted(Comparator.comparing(UUID::toString)).toList();
        List<InventoryItem> inventories = inventoryItemRepository
                .findByWarehouseIdAndVariantIdInWithLock(warehouseId, variantIds);
        Map<UUID, InventoryItem> byVariant = new HashMap<>();
        inventories.forEach(value -> byVariant.put(value.getVariant().getId(), value));
        List<WaitingStockItemResponse> missing = new ArrayList<>();
        for (Requirement requirement : requirements) {
            InventoryItem inventory = byVariant.get(requirement.variant().getId());
            int physicalAvailable = inventory == null ? 0
                    : safe(inventory.getQuantityOnHand()) - safe(inventory.getReservedQuantity());
            if (physicalAvailable < requirement.quantity()) {
                missing.add(new WaitingStockItemResponse(requirement.variant().getId(), requirement.variant().getSku(),
                        requirement.variant().getName(), requirement.quantity(), Math.max(0, physicalAvailable),
                        requirement.quantity() - Math.max(0, physicalAvailable)));
            }
        }
        if (!missing.isEmpty()) return moveToWaiting(order, missing, "INSUFFICIENT_STOCK", true);

        order.setStatus(OrderStatus.PENDING);
        order.setStatusChangedAt(now);
        clearWaitingMetadata(order);
        Order saved = orderRepository.save(order);
        if (fromFifo) notifyAfterCommit(saved, "ORDER_WAITING_STOCK", "Đơn hàng đã có tồn kho",
                "Đơn đã được đưa lại về Chờ xử lý. Sales có thể xác nhận để giữ tồn kho.", false);
        return saved;
    }

    private Order moveToWaiting(Order order, List<WaitingStockItemResponse> items, String reason, boolean fifoEligible) {
        OffsetDateTime now = OffsetDateTime.now();
        order.setStatus(OrderStatus.WAITING_STOCK);
        order.setStatusChangedAt(now);
        if (order.getWaitingStockAt() == null) order.setWaitingStockAt(now);
        if (order.getWaitingStockExpiresAt() == null) {
            order.setWaitingStockExpiresAt(now.plusHours(waitingTimeoutHours));
        }
        Map<String, Object> metadata = new LinkedHashMap<>(
                order.getPlatformMetadata() == null ? Map.of() : order.getPlatformMetadata());
        Map<String, Object> waiting = new LinkedHashMap<>();
        waiting.put("reason", reason);
        waiting.put("fifoEligible", fifoEligible);
        waiting.put("items", items.stream().map(this::itemMap).toList());
        metadata.put("waitingStock", waiting);
        order.setPlatformMetadata(metadata);
        Order saved = orderRepository.save(order);
        notifyAfterCommit(saved, "ORDER_WAITING_STOCK", "Đơn hàng đang chờ tồn kho",
                "Đơn " + saved.getExternalOrderId() + " chưa đủ tồn kho để xử lý.");
        return saved;
    }

    private List<Requirement> requirements(Order order) {
        Map<String, Requirement> result = new LinkedHashMap<>();
        for (OrderItem item : orderItemRepository.findByOrderId(order.getId())) {
            ProductVariant variant = item.getVariant();
            if (variant == null && item.getSku() != null && !item.getSku().isBlank()) {
                variant = variantRepository.findBySkuAndDeletedAtIsNull(item.getSku()).orElse(null);
            }
            String key = variant == null ? "ITEM:" + item.getId() : variant.getId().toString();
            ProductVariant resolved = variant;
            result.merge(key, new Requirement(resolved, safe(item.getQuantity()), item),
                    (left, right) -> new Requirement(left.variant(), left.quantity() + right.quantity(), left.item()));
        }
        return new ArrayList<>(result.values());
    }

    private List<WaitingStockItemResponse> unavailableItems(
            Collection<Requirement> requirements, Map<UUID, InventoryItem> inventories) {
        List<WaitingStockItemResponse> missing = new ArrayList<>();
        for (Requirement requirement : requirements) {
            InventoryItem inventory = inventories.get(requirement.variant().getId());
            int available = inventory == null ? 0
                    : safe(inventory.getQuantityOnHand()) - safe(inventory.getReservedQuantity());
            if (available < requirement.quantity()) {
                missing.add(new WaitingStockItemResponse(
                        requirement.variant().getId(), requirement.variant().getSku(), requirement.variant().getName(),
                        requirement.quantity(), available, requirement.quantity() - available));
            }
        }
        return missing;
    }

    private List<WaitingStockItemResponse> unavailableForConfirm(List<Requirement> requirements) {
        UUID warehouseId = warehouseConsistencyService.resolveMasterWarehouse().getId();
        List<UUID> variantIds = requirements.stream().map(value -> value.variant().getId()).distinct()
                .sorted(Comparator.comparing(UUID::toString)).toList();
        Map<UUID, InventoryItem> inventories = new HashMap<>();
        inventoryItemRepository.findByWarehouseIdAndVariantIdInWithLock(warehouseId, variantIds)
                .forEach(value -> inventories.put(value.getVariant().getId(), value));
        List<WaitingStockItemResponse> missing = new ArrayList<>();
        for (Requirement requirement : requirements) {
            InventoryItem inventory = inventories.get(requirement.variant().getId());
            int available = inventory == null ? 0
                    : safe(inventory.getQuantityOnHand()) - safe(inventory.getReservedQuantity());
            if (available < requirement.quantity()) {
                missing.add(new WaitingStockItemResponse(
                        requirement.variant().getId(), requirement.variant().getSku(), requirement.variant().getName(),
                        requirement.quantity(), available, requirement.quantity() - available));
            }
        }
        return missing;
    }

    private List<WaitingStockItemResponse> missingForInvalid(List<Requirement> requirements) {
        return requirements.stream().filter(value -> value.variant() == null)
                .map(value -> new WaitingStockItemResponse(null, value.item().getSku(), value.item().getName(),
                        value.quantity(), 0, value.quantity())).toList();
    }

    private boolean isExpired(Order order) {
        return order.getWaitingStockExpiresAt() != null
                && !order.getWaitingStockExpiresAt().isAfter(OffsetDateTime.now());
    }

    private boolean hasPlatformConflict(Order order) {
        if (order.getPlatformMetadata() == null) return false;
        Object raw = order.getPlatformMetadata().get("platformProgressConflict");
        return raw instanceof Map<?, ?> conflict && Boolean.TRUE.equals(conflict.get("active"));
    }

    private boolean isFifoEligible(Order order) {
        if (order.getPlatformMetadata() == null) return true;
        Object raw = order.getPlatformMetadata().get("waitingStock");
        if (!(raw instanceof Map<?, ?> waiting)) return true;
        Object eligible = waiting.get("fifoEligible");
        return eligible == null || Boolean.parseBoolean(String.valueOf(eligible));
    }

    private void clearAllocation(Order order) {
        order.setWaitingStockAt(null);
        order.setWaitingStockExpiresAt(null);
        order.setWaitingStockExpiryNotifiedAt(null);
        clearWaitingMetadata(order);
    }

    private void clearWaitingMetadata(Order order) {
        if (order.getPlatformMetadata() == null) return;
        Map<String, Object> metadata = new LinkedHashMap<>(order.getPlatformMetadata());
        metadata.remove("waitingStock");
        metadata.remove("platformProgressConflict");
        order.setPlatformMetadata(metadata);
    }

    private Map<String, Object> itemMap(WaitingStockItemResponse item) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("variantId", item.variantId() == null ? null : item.variantId().toString());
        value.put("sku", item.sku());
        value.put("name", item.name());
        value.put("required", item.required());
        value.put("available", item.available());
        value.put("missing", item.missing());
        return value;
    }

    private void notifyAfterCommit(Order order, String type, String title, String body) {
        notifyAfterCommit(order, type, title, body, true);
    }

    private void notifyAfterCommit(Order order, String type, String title, String body, boolean once) {
        Runnable action = () -> {
            if (once) {
                notificationService.notifyRolesOnce(
                        List.of("OWNER", "SALES"), type, title, body, "ORDER", order.getId());
            } else {
                notificationService.notifyRoles(
                        List.of("OWNER", "SALES"), type, title, body, "ORDER", order.getId());
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { action.run(); }
        });
    }

    private Order locked(UUID id) {
        return orderRepository.findForUpdateById(id).orElseThrow();
    }

    private int safe(Integer value) { return value == null ? 0 : value; }

    private record Requirement(ProductVariant variant, int quantity, OrderItem item) {}
}
