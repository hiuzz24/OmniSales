package fu.osms.inventory.service.impl;

import fu.osms.auth.entity.User;
import fu.osms.auth.repository.UserRepository;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.catalog.repository.ProductVariantRepository;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.inventory.dto.request.OrderStockDeliveryBatchRequest;
import fu.osms.inventory.dto.response.*;
import fu.osms.inventory.entity.*;
import fu.osms.inventory.enums.InvTxnType;
import fu.osms.inventory.mapper.StockDeliveryMapper;
import fu.osms.inventory.repository.InventoryIssueRepository;
import fu.osms.inventory.repository.InventoryItemRepository;
import fu.osms.inventory.repository.InventoryTransactionRepository;
import fu.osms.inventory.service.InventoryAlertService;
import fu.osms.inventory.service.OrderStockDeliveryService;
import fu.osms.order.entity.Order;
import fu.osms.order.entity.OrderItem;
import fu.osms.order.enums.OrderStatus;
import fu.osms.order.repository.OrderItemRepository;
import fu.osms.order.repository.OrderRepository;
import fu.osms.sync.service.MarketplaceWarehouseConsistencyService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.Year;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderStockDeliveryServiceImpl implements OrderStockDeliveryService {

    private static final String ORDER_ISSUE = "ORDER";
    private static final String ORDER_REFERENCE = "ORDER";
    private static final String ISSUE_REFERENCE = "ISSUE";

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final InventoryIssueRepository issueRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final ProductVariantRepository variantRepository;
    private final UserRepository userRepository;
    private final MarketplaceWarehouseConsistencyService warehouseConsistencyService;
    private final InventoryAlertService inventoryAlertService;
    private final StockDeliveryMapper stockDeliveryMapper;
    private final OrderStockDeliveryBatchService batchService;

    @Override
    @Transactional(readOnly = true)
    public Page<OrderStockDeliveryCandidateResponse> getCandidates(String keyword, Pageable pageable) {
        String normalizedKeyword = keyword == null ? null : keyword.trim();
        return orderRepository.findStockDeliveryCandidates(
                OrderStatus.PROCESSING, normalizedKeyword, pageable).map(this::toCandidate);
    }

    @Override
    public OrderStockDeliveryBatchResponse createFromOrders(OrderStockDeliveryBatchRequest request) {
        return batchService.create(request);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StockDeliveryResponse createFromOrder(UUID orderId) {
        Order order = orderRepository.findForUpdateById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        if (order.getStatus() != OrderStatus.PROCESSING) {
            throw new AppException(ErrorCode.ORDER_STATUS_INVALID_TRANSITION,
                    "Chỉ đơn hàng đang xử lý mới được tạo phiếu xuất kho");
        }
        if (!issueRepository.findByReferenceIdAndIssueTypeAndStatusIn(
                orderId, ORDER_ISSUE, List.of("DRAFT", "CONFIRMED")).isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Đơn hàng đã có phiếu xuất đang hoạt động");
        }

        Warehouse warehouse = warehouseConsistencyService.resolveMasterWarehouse();
        List<ResolvedOrderItem> resolvedItems = resolveOrderItems(order);
        validateReservation(order, warehouse, resolvedItems);

        InventoryIssue issue = InventoryIssue.builder()
                .warehouse(warehouse)
                .issueCode(generateIssueCode())
                .issueType(ORDER_ISSUE)
                .status("DRAFT")
                .referenceId(order.getId())
                .recipient(resolveRecipient(order))
                .notes("Phiếu xuất được tạo từ đơn hàng " + order.getExternalOrderId())
                .createdBy(getCurrentUser())
                .build();
        resolvedItems.forEach(resolved -> issue.addItem(InventoryIssueItem.builder()
                .productVariant(resolved.variant())
                .quantity(resolved.item().getQuantity())
                .unitCost(resolveUnitCost(resolved))
                .notes(resolved.item().getName())
                .build()));
        issue.calculateTotals();
        return stockDeliveryMapper.toResponse(issueRepository.save(issue));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeForOrder(UUID orderId) {
        Order order = orderRepository.findForUpdateById(orderId).orElse(null);
        if (order == null || (order.getStatus() != OrderStatus.IN_TRANSIT
                && order.getStatus() != OrderStatus.DELIVERED)) {
            return;
        }
        InventoryIssue issue = issueRepository
                .findFirstByReferenceIdAndIssueTypeAndStatus(orderId, ORDER_ISSUE, "DRAFT")
                .flatMap(candidate -> issueRepository.findByIdForUpdate(candidate.getId()))
                .orElse(null);
        if (issue == null || transactionRepository.existsByReferenceTypeAndReferenceIdAndType(
                ISSUE_REFERENCE, issue.getId(), InvTxnType.OUTBOUND)) {
            return;
        }
        commitOrderReservation(issue, order);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancelDraftForOrder(UUID orderId) {
        orderRepository.findForUpdateById(orderId).ifPresent(order ->
                issueRepository.findFirstByReferenceIdAndIssueTypeAndStatus(
                                orderId, ORDER_ISSUE, "DRAFT")
                        .flatMap(issue -> issueRepository.findByIdForUpdate(issue.getId()))
                        .ifPresent(issue -> {
                            issue.setStatus("CANCELLED");
                            issueRepository.save(issue);
                        }));
    }

    private void commitOrderReservation(InventoryIssue issue, Order order) {
        List<InventoryTransaction> reservations = orderReservations(order.getId());
        if (reservations.isEmpty()) {
            throw new AppException(ErrorCode.INSUFFICIENT_STOCK,
                    "Đơn hàng chưa có reservation để hoàn thành phiếu xuất");
        }
        UUID warehouseId = issue.getWarehouse().getId();
        if (reservations.stream().anyMatch(tx -> !warehouseId.equals(tx.getWarehouse().getId()))) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Đơn hàng đang giữ hàng ngoài Kho mặc định đa sàn");
        }
        Map<UUID, Integer> issueQuantities = issue.getItems().stream().collect(Collectors.toMap(
                item -> item.getProductVariant().getId(),
                item -> safeQuantity(item.getQuantity()),
                Integer::sum));
        Map<UUID, Integer> reservedQuantities = reservationQuantities(reservations);
        if (!reservedQuantities.equals(issueQuantities)) {
            throw new AppException(ErrorCode.INSUFFICIENT_STOCK,
                    "Reservation của đơn hàng không khớp với phiếu xuất");
        }

        User actor = issue.getCreatedBy();
        for (Map.Entry<UUID, Integer> entry : reservedQuantities.entrySet()) {
            InventoryItem inventory = inventoryItemRepository
                    .findByWarehouseIdAndVariantIdWithLock(warehouseId, entry.getKey())
                    .orElseThrow(() -> new AppException(ErrorCode.INVENTORY_ITEM_NOT_FOUND));
            int quantity = entry.getValue();
            int onHandBefore = safeQuantity(inventory.getQuantityOnHand());
            int reservedBefore = safeQuantity(inventory.getReservedQuantity());
            if (onHandBefore < quantity || reservedBefore < quantity) {
                throw new AppException(ErrorCode.INSUFFICIENT_STOCK,
                        "Tồn kho hoặc reservation không đủ để hoàn thành phiếu xuất");
            }
            inventory.setQuantityOnHand(onHandBefore - quantity);
            inventory.setReservedQuantity(reservedBefore - quantity);
            inventory.setUpdatedBy(actor);
            inventoryItemRepository.save(inventory);
            inventoryAlertService.notifyLowStockAfterStockChange(inventory);

            InventoryIssueItem issueItem = issue.getItems().stream()
                    .filter(item -> entry.getKey().equals(item.getProductVariant().getId()))
                    .findFirst().orElseThrow();
            transactionRepository.save(InventoryTransaction.builder()
                    .warehouse(issue.getWarehouse())
                    .variant(inventory.getVariant())
                    .type(InvTxnType.OUTBOUND)
                    .referenceType(ISSUE_REFERENCE)
                    .referenceId(issue.getId())
                    .quantityChange(-quantity)
                    .quantityBefore(onHandBefore)
                    .quantityAfter(onHandBefore - quantity)
                    .unitCost(issueItem.getUnitCost())
                    .performedBy(actor)
                    .note("Xuất kho cho đơn hàng " + order.getExternalOrderId())
                    .build());
        }
        issue.setStatus("CONFIRMED");
        issue.setApprovedBy(actor);
        issue.setConfirmedAt(OffsetDateTime.now());
        issueRepository.save(issue);
    }

    private void validateReservation(Order order, Warehouse warehouse, List<ResolvedOrderItem> items) {
        List<InventoryTransaction> reservations = orderReservations(order.getId());
        if (reservations.isEmpty()) {
            throw new AppException(ErrorCode.INSUFFICIENT_STOCK,
                    "Đơn hàng chưa được giữ hàng đầy đủ");
        }
        if (reservations.stream().anyMatch(tx -> !warehouse.getId().equals(tx.getWarehouse().getId()))) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Đơn hàng đang giữ hàng sai kho; yêu cầu Kho mặc định đa sàn");
        }
        Map<UUID, Integer> required = items.stream().collect(Collectors.toMap(
                item -> item.variant().getId(),
                item -> safeQuantity(item.item().getQuantity()),
                Integer::sum));
        Map<UUID, Integer> reserved = reservationQuantities(reservations);
        if (!reserved.keySet().equals(required.keySet())) {
            throw new AppException(ErrorCode.INSUFFICIENT_STOCK,
                    "Reservation của đơn hàng không khớp với danh sách sản phẩm hiện tại");
        }
        for (Map.Entry<UUID, Integer> entry : required.entrySet()) {
            if (!Objects.equals(reserved.get(entry.getKey()), entry.getValue())) {
                throw new AppException(ErrorCode.INSUFFICIENT_STOCK,
                        "Đơn hàng chưa được giữ hàng đầy đủ cho SKU " + sku(items, entry.getKey()));
            }
        }
    }

    private List<InventoryTransaction> orderReservations(UUID orderId) {
        if (transactionRepository.existsByReferenceTypeAndReferenceIdAndType(
                ORDER_REFERENCE, orderId, InvTxnType.ORDER_CANCEL)) {
            return List.of();
        }
        return transactionRepository.findByReferenceTypeAndReferenceIdAndType(
                ORDER_REFERENCE, orderId, InvTxnType.ORDER_DEDUCT);
    }

    private Map<UUID, Integer> reservationQuantities(List<InventoryTransaction> reservations) {
        return reservations.stream().collect(Collectors.toMap(
                tx -> tx.getVariant().getId(),
                tx -> Math.abs(safeQuantity(tx.getQuantityChange())),
                Integer::sum,
                LinkedHashMap::new));
    }

    private List<ResolvedOrderItem> resolveOrderItems(Order order) {
        List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
        if (items.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Đơn hàng không có sản phẩm");
        }
        return items.stream().map(item -> {
            ProductVariant variant = item.getVariant();
            if (variant == null && item.getSku() != null && !item.getSku().isBlank()) {
                variant = variantRepository.findBySkuAndDeletedAtIsNull(item.getSku()).orElse(null);
            }
            if (variant == null) {
                throw new AppException(ErrorCode.VARIANT_NOT_FOUND,
                        "Không thể tạo phiếu: SKU " + Objects.toString(item.getSku(), "-")
                                + " chưa được liên kết với sản phẩm OSMS");
            }
            return new ResolvedOrderItem(item, variant);
        }).toList();
    }

    private OrderStockDeliveryCandidateResponse toCandidate(Order order) {
        List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
        List<OrderStockDeliveryCandidateItemResponse> itemResponses = items.stream()
                .map(item -> new OrderStockDeliveryCandidateItemResponse(
                        item.getId(), item.getVariant() == null ? null : item.getVariant().getId(),
                        item.getSku(), item.getName(), item.getQuantity()))
                .toList();
        int totalQuantity = items.stream().mapToInt(item -> safeQuantity(item.getQuantity())).sum();
        return new OrderStockDeliveryCandidateResponse(
                order.getId(), order.getExternalOrderId(), order.getPlatform(), order.getChannelName(),
                order.getBuyerName(), order.getBuyerPhone(), order.getCreatedAt(), totalQuantity, itemResponses);
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    private String resolveRecipient(Order order) {
        if (order.getBuyerName() != null && !order.getBuyerName().isBlank()) return order.getBuyerName();
        if (order.getCustomer() != null && order.getCustomer().getFullName() != null
                && !order.getCustomer().getFullName().isBlank()) return order.getCustomer().getFullName();
        return order.getExternalOrderId();
    }

    private BigDecimal resolveUnitCost(ResolvedOrderItem item) {
        if (item.item().getCostPrice() != null) return item.item().getCostPrice();
        return item.variant().getCostPrice() == null ? BigDecimal.ZERO : item.variant().getCostPrice();
    }

    private String generateIssueCode() {
        return "PX-" + Year.now().getValue() + "-" +
                UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private String sku(List<ResolvedOrderItem> items, UUID variantId) {
        return items.stream().filter(item -> variantId.equals(item.variant().getId()))
                .map(item -> item.variant().getSku()).filter(Objects::nonNull).findFirst().orElse("-");
    }

    private int safeQuantity(Integer value) {
        return value == null ? 0 : value;
    }

    private record ResolvedOrderItem(OrderItem item, ProductVariant variant) {
    }
}
