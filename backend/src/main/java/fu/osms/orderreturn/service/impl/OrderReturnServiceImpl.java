package fu.osms.orderreturn.service.impl;

import fu.osms.common.dto.PageResponse;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.inventory.entity.Warehouse;
import fu.osms.orderreturn.dto.request.OrderReturnInspectionRequest;
import fu.osms.orderreturn.dto.response.OrderReturnItemResponse;
import fu.osms.orderreturn.dto.response.OrderReturnResponse;
import fu.osms.orderreturn.entity.OrderReturn;
import fu.osms.orderreturn.entity.OrderReturnItem;
import fu.osms.orderreturn.enums.OrderReturnStatus;
import fu.osms.orderreturn.enums.ReturnAction;
import fu.osms.orderreturn.event.OrderReturnInspectedEvent;
import fu.osms.orderreturn.repository.OrderReturnItemRepository;
import fu.osms.orderreturn.repository.OrderReturnRepository;
import fu.osms.orderreturn.service.OrderReturnActionService;
import fu.osms.orderreturn.service.OrderReturnInventoryPostingService;
import fu.osms.orderreturn.service.OrderReturnService;
import fu.osms.sync.service.MarketplaceWarehouseConsistencyService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OrderReturnServiceImpl implements OrderReturnService {

    private final OrderReturnRepository returnRepository;
    private final OrderReturnItemRepository itemRepository;
    private final MarketplaceWarehouseConsistencyService warehouseConsistencyService;
    private final OrderReturnActionService actionService;
    private final OrderReturnInventoryPostingService inventoryPostingService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderReturnResponse> getAll(int page, int size) {
        Page<OrderReturn> result = returnRepository.findAllWithDetails(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageResponse.<OrderReturnResponse>builder()
                .content(result.getContent().stream().map(this::toResponse).toList())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .first(result.isFirst())
                .last(result.isLast())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public OrderReturnResponse getById(UUID id) {
        return toResponse(returnRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_RETURN_NOT_FOUND)));
    }

    @Override
    public OrderReturnResponse approve(UUID id) {
        actionService.execute(id, ReturnAction.APPROVE, null);
        return getById(id);
    }

    @Override
    public OrderReturnResponse reject(UUID id, String reason) {
        actionService.execute(id, ReturnAction.REJECT, reason);
        return getById(id);
    }

    @Override
    @Transactional
    public OrderReturnResponse inspect(UUID id, OrderReturnInspectionRequest request) {
        OrderReturn orderReturn = returnRepository.findForUpdateById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_RETURN_NOT_FOUND));
        if (orderReturn.getStatus() != OrderReturnStatus.AWAITING_RETURN
                && orderReturn.getStatus() != OrderReturnStatus.RETURN_IN_TRANSIT) {
            throw new AppException(ErrorCode.ORDER_RETURN_INVALID_STATE);
        }
        List<OrderReturnItem> items = itemRepository.findByReturnIdWithDetails(id);
        Map<UUID, OrderReturnItem> byId = new HashMap<>();
        items.forEach(item -> byId.put(item.getId(), item));
        if (request.items().size() != items.size()) {
            throw new AppException(ErrorCode.ORDER_RETURN_QUANTITY_INVALID,
                    "Inspection must include every return item");
        }
        for (OrderReturnInspectionRequest.Item value : request.items()) {
            OrderReturnItem item = byId.get(value.returnItemId());
            if (item == null) {
                throw new AppException(
                        ErrorCode.ORDER_RETURN_QUANTITY_INVALID,
                        "Sản phẩm trả hàng đã được cập nhật. Vui lòng tải lại trang trước khi kiểm hàng.");
            }
            if (value.receivedQuantity() != value.restockableQuantity() + value.damagedQuantity()
                    || value.receivedQuantity() + value.missingQuantity() != item.getApprovedQuantity()) {
                throw new AppException(
                        ErrorCode.ORDER_RETURN_QUANTITY_INVALID,
                        "Số lượng không hợp lệ cho SKU " + item.getSnapshotSku()
                                + ": đã nhận phải bằng hàng đạt + hàng hỏng, "
                                + "và đã nhận + hàng thiếu phải bằng " + item.getApprovedQuantity() + ".");
            }
            item.setReceivedQuantity(value.receivedQuantity());
            item.setRestockableQuantity(value.restockableQuantity());
            item.setDamagedQuantity(value.damagedQuantity());
            item.setMissingQuantity(value.missingQuantity());
        }
        Warehouse warehouse = warehouseConsistencyService.resolveMasterWarehouse();
        orderReturn.setWarehouse(warehouse);
        orderReturn.setInspectedAt(OffsetDateTime.now());
        orderReturn.setStatus(OrderReturnStatus.INSPECTED);
        itemRepository.saveAll(items);
        OrderReturn saved = returnRepository.save(orderReturn);
        eventPublisher.publishEvent(new OrderReturnInspectedEvent(saved.getId()));
        return toResponse(saved);
    }

    @Override
    public OrderReturnResponse checkAction(UUID id) {
        requireActionRole(id);
        actionService.check(id);
        return getById(id);
    }

    @Override
    public OrderReturnResponse retryAction(UUID id) {
        requireActionRole(id);
        actionService.retry(id);
        return getById(id);
    }

    @Override
    public OrderReturnResponse retryStock(UUID id) {
        try {
            inventoryPostingService.postIfReady(id);
        } catch (RuntimeException exception) {
            inventoryPostingService.markPending(id, rootMessage(exception));
            throw exception;
        }
        return getById(id);
    }

    private OrderReturnResponse toResponse(OrderReturn orderReturn) {
        List<OrderReturnItemResponse> items = itemRepository.findByReturnIdWithDetails(orderReturn.getId()).stream()
                .map(this::toItemResponse)
                .toList();
        return new OrderReturnResponse(
                orderReturn.getId(),
                orderReturn.getOrder().getId(),
                orderReturn.getOrder().getExternalOrderId(),
                orderReturn.getChannel().getId(),
                orderReturn.getChannel().getDisplayName(),
                orderReturn.getPlatform(),
                orderReturn.getWarehouse() == null ? null : orderReturn.getWarehouse().getId(),
                orderReturn.getWarehouse() == null ? null : orderReturn.getWarehouse().getName(),
                orderReturn.getExternalReturnId(),
                orderReturn.getPlatformStatus(),
                orderReturn.getStatus(),
                orderReturn.getDataValidationState(),
                orderReturn.getLastAction(),
                orderReturn.getActionState(),
                orderReturn.getActionError(),
                orderReturn.getLastSyncError(),
                orderReturn.getPlatformUpdatedAt(),
                orderReturn.getApprovedAt(),
                orderReturn.getInspectedAt(),
                orderReturn.getRefundConfirmedAt(),
                orderReturn.getInventoryPostedAt(),
                orderReturn.getCreatedAt(),
                orderReturn.getUpdatedAt(),
                items);
    }

    private OrderReturnItemResponse toItemResponse(OrderReturnItem item) {
        return new OrderReturnItemResponse(
                item.getId(),
                item.getOrderItem() == null ? null : item.getOrderItem().getId(),
                item.getExternalOrderItemId(),
                item.getExternalReturnItemId(),
                item.getSnapshotSku(),
                item.getSnapshotName(),
                item.getRequestedQuantity(),
                item.getApprovedQuantity(),
                item.getReceivedQuantity(),
                item.getRestockableQuantity(),
                item.getDamagedQuantity(),
                item.getMissingQuantity(),
                item.getRefundedQuantity(),
                item.getSnapshotUnitPrice());
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    @Transactional(readOnly = true)
    protected void requireActionRole(UUID id) {
        OrderReturn orderReturn = returnRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_RETURN_NOT_FOUND));
        if (orderReturn.getLastAction() == null) {
            throw new AccessDeniedException("Return has no action to manage");
        }
        boolean allowed = switch (orderReturn.getLastAction()) {
            case APPROVE, REJECT -> hasAnyRole("OWNER", "SALES");
            case PROCESS -> hasAnyRole("OWNER", "OPERATIONS");
        };
        if (!allowed) {
            throw new AccessDeniedException("You cannot manage this return action");
        }
    }

    private boolean hasAnyRole(String... roles) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) return false;
        Set<String> expected = Arrays.stream(roles)
                .map(role -> "ROLE_" + role)
                .collect(java.util.stream.Collectors.toSet());
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> expected.contains(authority.getAuthority()));
    }
}
