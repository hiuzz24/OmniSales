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

    /** Sales xác nhận thủ công order đang chờ hàng và thử giữ tồn kho thật. */
    @Override
    @Transactional
    public Order confirmOrder(UUID orderId) {
        Order order = locked(orderId);
        Order result = applyHardReservation(order);
        inventoryPropagationService.scheduleWaitingStockReconcile(variantIds(order));
        return result;
    }

    /**
     * Kiểm tra lại tồn kho cho order đang WAITING_STOCK.
     * Hàm chỉ cập nhật cờ "đã có hàng" và số lượng thiếu, không tự giữ tồn.
     */
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

    /** Thử giữ tồn thật cho toàn bộ SKU; đủ thì xác nhận, thiếu thì chuyển sang chờ hàng. */
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

    /** Chuyển order sang WAITING_STOCK và lưu lý do cùng các SKU đang thiếu. */
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

    /** Gom các order item theo variant và cộng tổng số lượng cần giữ. */
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

    /** Đọc tồn khả dụng tại kho chính và khóa các dòng inventory liên quan. */
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

    /** Kiểm tra order đã quá thời hạn chờ được cấp tồn hay chưa. */
    private boolean isExpired(Order order) {
        return order.getWaitingStockExpiresAt() != null
                && !order.getWaitingStockExpiresAt().isAfter(OffsetDateTime.now());
    }

    /** Kiểm tra platform đã tiến trạng thái ngoài OSMS và cần đối soát hay chưa. */
    private boolean hasPlatformConflict(Order order) {
        if (order.getPlatformMetadata() == null) return false;
        Object raw = order.getPlatformMetadata().get("platformProgressConflict");
        return raw instanceof Map<?, ?> conflict && Boolean.TRUE.equals(conflict.get("active"));
    }

    /** Cập nhật cờ "Đã có hàng" và snapshot tồn; không tự giữ tồn cho order. */
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

    /** Đọc lại danh sách SKU thiếu đã lưu trong metadata của order. */
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

    /** Lấy các variant của order để phát event kiểm tra lại những order liên quan. */
    private Set<UUID> variantIds(Order order) {
        return requirements(order).stream().map(Requirement::variant).filter(java.util.Objects::nonNull)
                .map(ProductVariant::getId).collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    /** Xóa thời gian chờ và metadata phân bổ sau khi order đã giữ hàng thành công. */
    private void clearAllocation(Order order) {
        order.setWaitingStockAt(null);
        order.setWaitingStockExpiresAt(null);
        order.setWaitingStockExpiryNotifiedAt(null);
        clearWaitingMetadata(order);
    }

    /** Xóa nhánh waitingStock và cảnh báo xung đột platform khỏi metadata. */
    private void clearWaitingMetadata(Order order) {
        if (order.getPlatformMetadata() == null) return;
        Map<String, Object> metadata = new LinkedHashMap<>(order.getPlatformMetadata());
        metadata.remove("waitingStock");
        metadata.remove("platformProgressConflict");
        order.setPlatformMetadata(metadata);
    }

    /** Chuyển thông tin SKU thiếu thành Map để lưu trong JSON metadata. */
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

    /** Gửi notification sau khi transaction commit và mặc định chống gửi trùng. */
    private void notifyAfterCommit(Order order, String type, String title, String body) {
        notifyAfterCommit(order, type, title, body, true);
    }

    /** Đăng ký gửi notification sau commit, có thể chọn gửi một lần hoặc mỗi lần. */
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

    /** Lấy và khóa order bằng pessimistic lock để tránh hai transaction xử lý đồng thời. */
    private Order locked(UUID id) {
        return orderRepository.findForUpdateById(id).orElseThrow();
    }

    /** Chuyển giá trị Integer null thành 0 khi tính tồn hoặc số lượng. */
    private int safe(Integer value) { return value == null ? 0 : value; }

    /** Đọc số nguyên an toàn từ dữ liệu metadata. */
    private int number(Object value) {
        if (value instanceof Number number) return number.intValue();
        try { return value == null ? 0 : Integer.parseInt(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return 0; }
    }

    /** Đọc chuỗi an toàn từ dữ liệu metadata. */
    private String text(Object value) { return value == null ? null : String.valueOf(value); }

    /** Đọc UUID an toàn từ dữ liệu metadata. */
    private UUID uuid(Object value) {
        try { return value == null ? null : UUID.fromString(String.valueOf(value)); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    /** Nhu cầu tồn đã được gom theo một variant trong order. */
    private record Requirement(ProductVariant variant, int quantity, OrderItem item) {}
}
