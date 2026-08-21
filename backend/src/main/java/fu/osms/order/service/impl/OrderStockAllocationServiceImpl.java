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
import fu.osms.order.support.TikTokBuyerCancellationMetadata;
import fu.osms.order.repository.OrderItemRepository;
import fu.osms.order.repository.OrderRepository;
import fu.osms.order.service.OrderStockAllocationService;
import fu.osms.notification.service.OrderWorkflowNotificationService;
import fu.osms.sync.service.MarketplaceWarehouseConsistencyService;
import fu.osms.sync.service.MarketplaceInventoryPropagationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.util.ArrayList;
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
            return applyHardReservation(order);
        }
        return order;
    }

    @Override
    @Transactional
    public Order confirmOrder(UUID orderId) {
        Order order = locked(orderId);
        Order result = applyHardReservation(order);
        inventoryPropagationService.scheduleWaitingStockReconcile(variantIds(order));
        return result;
    }

    /** Làm mới khả năng xác nhận của order mà không tự giữ tồn hoặc đổi trạng thái. */
    @Override
    @Transactional
    public Order refreshWaitingStockAvailability(UUID orderId) {
        Order order = locked(orderId);
        if (TikTokBuyerCancellationMetadata.isActive(order)
                || order.getStatus() != OrderStatus.WAITING_STOCK
                || hasPlatformConflict(order)) return order;

        List<Requirement> requirements = requirements(order);
        boolean resolvable = requirements.stream().noneMatch(value -> value.variant() == null);
        List<WaitingStockItemResponse> items = resolvable
                ? availabilityFor(requirements)
                : waitingStockItems(order);
        boolean ready = resolvable && !isExpired(order)
                && items.stream().allMatch(item -> item.missing() <= 0);
        return updateReadiness(order, ready, items);
    }

    private Order applyHardReservation(Order order) {
        OffsetDateTime now = OffsetDateTime.now();
        if (order.getWaitingStockExpiresAt() != null && !order.getWaitingStockExpiresAt().isAfter(now)) {
            return moveToWaiting(order, List.of(), "ALLOCATION_EXPIRED");
        }
        ReservationOutcome outcome = inventoryService.tryReserve(order.getId());
        if (outcome.result() == ReservationResult.RESERVED
                || outcome.result() == ReservationResult.ALREADY_RESERVED) {
            order.setStatus(OrderStatus.CONFIRMED);
            order.setStatusChangedAt(now);
            clearAllocation(order);
            Order saved = orderRepository.save(order);
            return saved;
        }
        return moveToWaiting(order, outcome.missingItems(), outcome.result().name());
    }

    private Order moveToWaiting(Order order, List<WaitingStockItemResponse> items, String reason) {
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
        waiting.put("stockReadyForConfirmation", false);
        waiting.put("stockReadyAt", null);
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

    private List<WaitingStockItemResponse> availabilityFor(List<Requirement> requirements) {
        UUID warehouseId = warehouseConsistencyService.resolveMasterWarehouse().getId();
        List<UUID> variantIds = requirements.stream().map(value -> value.variant().getId()).distinct()
                .sorted(Comparator.comparing(UUID::toString)).toList();
        Map<UUID, InventoryItem> inventories = new HashMap<>();
        inventoryItemRepository.findByWarehouseIdAndVariantIdInWithLock(warehouseId, variantIds)
                .forEach(value -> inventories.put(value.getVariant().getId(), value));
        List<WaitingStockItemResponse> result = new ArrayList<>();
        for (Requirement requirement : requirements) {
            InventoryItem inventory = inventories.get(requirement.variant().getId());
            int available = inventory == null ? 0
                    : safe(inventory.getQuantityOnHand()) - safe(inventory.getReservedQuantity());
            result.add(new WaitingStockItemResponse(
                    requirement.variant().getId(), requirement.variant().getSku(), requirement.variant().getName(),
                    requirement.quantity(), available, Math.max(0, requirement.quantity() - available)));
        }
        return result;
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

    private Order updateReadiness(Order order, boolean ready, List<WaitingStockItemResponse> items) {
        Map<String, Object> metadata = new LinkedHashMap<>(
                order.getPlatformMetadata() == null ? Map.of() : order.getPlatformMetadata());
        Object raw = metadata.get("waitingStock");
        Map<String, Object> waiting = new LinkedHashMap<>();
        if (raw instanceof Map<?, ?> existing) {
            existing.forEach((key, value) -> waiting.put(String.valueOf(key), value));
        }
        boolean currentReady = Boolean.TRUE.equals(waiting.get("stockReadyForConfirmation"));
        String currentReadyAt = waiting.get("stockReadyAt") == null ? null
                : String.valueOf(waiting.get("stockReadyAt"));
        String nextReadyAt = ready
                ? (currentReadyAt == null ? OffsetDateTime.now().toString() : currentReadyAt)
                : null;
        List<Map<String, Object>> nextItems = items.stream().map(this::itemMap).toList();
        if (currentReady == ready && java.util.Objects.equals(currentReadyAt, nextReadyAt)
                && java.util.Objects.equals(waiting.get("items"), nextItems)) {
            return order;
        }
        waiting.put("stockReadyForConfirmation", ready);
        waiting.put("stockReadyAt", nextReadyAt);
        waiting.put("items", nextItems);
        metadata.put("waitingStock", waiting);
        order.setPlatformMetadata(metadata);
        return orderRepository.save(order);
    }

    private List<WaitingStockItemResponse> waitingStockItems(Order order) {
        Object raw = order.getPlatformMetadata() == null ? null : order.getPlatformMetadata().get("waitingStock");
        if (!(raw instanceof Map<?, ?> waiting) || !(waiting.get("items") instanceof List<?> values)) return List.of();
        List<WaitingStockItemResponse> result = new ArrayList<>();
        for (Object value : values) {
            if (!(value instanceof Map<?, ?> item)) continue;
            result.add(new WaitingStockItemResponse(
                    uuid(item.get("variantId")), text(item.get("sku")), text(item.get("name")),
                    number(item.get("required")), number(item.get("available")), number(item.get("missing"))));
        }
        return result;
    }

    private Set<UUID> variantIds(Order order) {
        return requirements(order).stream().map(Requirement::variant).filter(java.util.Objects::nonNull)
                .map(ProductVariant::getId).collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
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

    private int number(Object value) {
        if (value instanceof Number number) return number.intValue();
        try { return value == null ? 0 : Integer.parseInt(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return 0; }
    }

    private String text(Object value) { return value == null ? null : String.valueOf(value); }

    private UUID uuid(Object value) {
        try { return value == null ? null : UUID.fromString(String.valueOf(value)); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    private record Requirement(ProductVariant variant, int quantity, OrderItem item) {}
}
