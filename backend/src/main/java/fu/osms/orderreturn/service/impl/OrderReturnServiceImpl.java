package fu.osms.orderreturn.service.impl;

import fu.osms.common.dto.PageResponse;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.orderreturn.dto.request.OrderReturnInspectionRequest;
import fu.osms.orderreturn.dto.request.OrderReturnRejectRequest;
import fu.osms.orderreturn.dto.response.OrderReturnRejectOptionsResponse;
import fu.osms.orderreturn.dto.response.OrderReturnItemResponse;
import fu.osms.orderreturn.dto.response.OrderReturnResponse;
import fu.osms.orderreturn.entity.OrderReturn;
import fu.osms.orderreturn.entity.OrderReturnItem;
import fu.osms.orderreturn.enums.ReturnAction;
import fu.osms.orderreturn.model.ReturnRejectCommand;
import fu.osms.orderreturn.model.ReturnRejectOptions;
import fu.osms.orderreturn.repository.OrderReturnItemRepository;
import fu.osms.orderreturn.repository.OrderReturnRepository;
import fu.osms.orderreturn.service.OrderReturnActionService;
import fu.osms.orderreturn.service.OrderReturnInventoryPostingService;
import fu.osms.orderreturn.service.OrderReturnService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderReturnServiceImpl implements OrderReturnService {

    private final OrderReturnRepository returnRepository;
    private final OrderReturnItemRepository itemRepository;
    private final OrderReturnInspectionTransactionService inspectionTransactionService;
    private final OrderReturnActionService actionService;
    private final OrderReturnInventoryPostingService inventoryPostingService;

    /** Trả về danh sách phiếu trả hàng đã lưu có phân trang. */
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

    /** Tải một phiếu trả cùng số lượng kiểm hàng và hoàn tiền theo từng item. */
    @Override
    @Transactional(readOnly = true)
    public OrderReturnResponse getById(UUID id) {
        return toResponse(returnRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_RETURN_NOT_FOUND)));
    }

    /** Bắt đầu action APPROVE trên platform và trả về snapshot nội bộ mới nhất. */
    @Override
    public OrderReturnResponse approve(UUID id) {
        actionService.execute(id, ReturnAction.APPROVE, null);
        return getById(id);
    }

    /** Chuyển lựa chọn từ chối của platform thành nhãn hiển thị trên UI. */
    @Override
    public OrderReturnRejectOptionsResponse getRejectOptions(UUID id) {
        ReturnRejectOptions options = actionService.getRejectOptions(id);
        return new OrderReturnRejectOptionsResponse(
                options.requiresReasonCode(),
                options.allowsComment(),
                options.options().stream()
                        .map(option -> new OrderReturnRejectOptionsResponse.Option(option.code(), option.label()))
                        .toList(),
                options.unavailableReason());
    }

    /** Kiểm tra và gửi lệnh từ chối riêng của từng platform. */
    @Override
    public OrderReturnResponse reject(UUID id, OrderReturnRejectRequest request) {
        String legacyReason = trim(request.reason());
        String reasonCode = trim(request.reasonCode());
        String comment = trim(request.comment());
        if (legacyReason != null && (reasonCode != null || comment != null)) {
            throw new AppException(ErrorCode.INVALID_REQUEST,
                    "Không được gửi đồng thời reason và reasonCode/comment");
        }
        if (legacyReason != null) comment = legacyReason;
        actionService.execute(id, ReturnAction.REJECT, new ReturnRejectCommand(reasonCode, comment));
        return getById(id);
    }

    /** Kiểm tra trước trạng thái platform, lưu kiểm hàng rồi chạy PROCESS khi đủ điều kiện. */
    @Override
    public OrderReturnResponse inspect(UUID id, OrderReturnInspectionRequest request) {
        actionService.validateInspection(id);
        inspectionTransactionService.saveInspection(id, request);
        OrderReturnResponse inspected = getById(id);
        boolean partialRequiresManual = (inspected.platform() == PlatformType.SHOPIFY
                || inspected.platform() == PlatformType.TIKTOK)
                && inspected.items().stream().anyMatch(item -> item.missingQuantity() != null
                        && item.missingQuantity() > 0);
        if (partialRequiresManual) {
            return inspected;
        }
        actionService.execute(id, ReturnAction.PROCESS, null);
        return getById(id);
    }

    /** Làm mới trạng thái từ xa mà không duyệt, từ chối hoặc process phiếu trả. */
    @Override
    public OrderReturnResponse refresh(UUID id) {
        actionService.refresh(id);
        return getById(id);
    }

    /** Kiểm tra action platform chưa xác định có tạo side effect hay không. */
    @Override
    public OrderReturnResponse checkAction(UUID id) {
        requireActionRole(id);
        actionService.check(id);
        return getById(id);
    }

    /** Kiểm tra quyền theo action gần nhất trước khi thử lại bằng request ID hiện có. */
    @Override
    public OrderReturnResponse retryAction(UUID id) {
        requireActionRole(id);
        actionService.retry(id);
        return getById(id);
    }

    /** Chạy lại nhập kho nội bộ và không gọi API platform. */
    @Override
    public OrderReturnResponse retryStock(UUID id) {
        try {
            inventoryPostingService.postIfReady(id);
        } catch (RuntimeException exception) {
            try {
                inventoryPostingService.markPending(id, rootMessage(exception));
            } catch (RuntimeException pendingException) {
                exception.addSuppressed(pendingException);
            }
            throw exception;
        }
        return getById(id);
    }

    private OrderReturnResponse toResponse(OrderReturn orderReturn) {
        List<OrderReturnItemResponse> items = itemRepository.findByReturnIdWithDetails(orderReturn.getId()).stream()
                .map(this::toItemResponse)
                .toList();
        boolean actionRetryAllowed = !(orderReturn.getPlatform() == fu.osms.common.enums.PlatformType.TIKTOK
                && orderReturn.getLastAction() == ReturnAction.PROCESS
                && items.stream().anyMatch(item -> item.receivedQuantity() != null
                        && item.receivedQuantity() < item.approvedQuantity()));
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
                actionRetryAllowed,
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

    private void requireActionRole(UUID id) {
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
                .collect(Collectors.toSet());
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> expected.contains(authority.getAuthority()));
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
