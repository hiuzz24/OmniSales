package fu.osms.orderreturn.service.impl;

import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.orderreturn.entity.OrderReturn;
import fu.osms.orderreturn.entity.OrderReturnItem;
import fu.osms.orderreturn.enums.OrderReturnStatus;
import fu.osms.orderreturn.enums.ReturnAction;
import fu.osms.orderreturn.enums.ReturnActionState;
import fu.osms.orderreturn.enums.ReturnDataValidationState;
import fu.osms.orderreturn.model.ReturnActionContext;
import fu.osms.orderreturn.model.ReturnRejectCommand;
import fu.osms.orderreturn.repository.OrderReturnItemRepository;
import fu.osms.orderreturn.repository.OrderReturnRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderReturnActionStateService {

    private final OrderReturnRepository returnRepository;
    private final OrderReturnItemRepository itemRepository;

    /** Lưu trạng thái PROCESSING và request ID bền vững trước khi gọi platform. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ReturnActionContext beginNew(UUID returnId, ReturnAction action, ReturnRejectCommand rejectCommand) {
        OrderReturn orderReturn = lock(returnId);
        validateAction(orderReturn, action);
        if (orderReturn.getActionState() == ReturnActionState.PROCESSING
                || orderReturn.getActionState() == ReturnActionState.UNKNOWN) {
            throw new AppException(ErrorCode.ORDER_RETURN_ACTION_IN_PROGRESS);
        }
        orderReturn.setLastAction(action);
        orderReturn.setActionState(ReturnActionState.PROCESSING);
        orderReturn.setActionRequestId(UUID.randomUUID());
        orderReturn.setActionError(null);
        if (action == ReturnAction.REJECT) {
            storeRejectCommand(orderReturn, rejectCommand);
        }
        if (action == ReturnAction.PROCESS) {
            orderReturn.setStatus(OrderReturnStatus.PLATFORM_PROCESSING);
        }
        returnRepository.save(orderReturn);
        return context(orderReturn);
    }

    /** Tái sử dụng request identity trước đó cho lần thử lại được phép. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ReturnActionContext beginRetry(UUID returnId) {
        OrderReturn orderReturn = lock(returnId);
        if (orderReturn.getActionState() != ReturnActionState.FAILED || orderReturn.getLastAction() == null) {
            throw new AppException(ErrorCode.ORDER_RETURN_ACTION_NOT_RETRYABLE);
        }
        validateAction(orderReturn, orderReturn.getLastAction());
        if (orderReturn.getActionRequestId() == null) {
            orderReturn.setActionRequestId(UUID.randomUUID());
        }
        orderReturn.setActionState(ReturnActionState.PROCESSING);
        orderReturn.setActionError(null);
        if (orderReturn.getLastAction() == ReturnAction.PROCESS) {
            orderReturn.setStatus(OrderReturnStatus.PLATFORM_PROCESSING);
        }
        returnRepository.save(orderReturn);
        return context(orderReturn);
    }

    /** Tạo action context chỉ đọc để xác minh kết quả từ xa. */
    @Transactional(readOnly = true)
    public ReturnActionContext contextForCheck(UUID returnId) {
        OrderReturn orderReturn = returnRepository.findById(returnId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_RETURN_NOT_FOUND));
        if (orderReturn.getActionState() != ReturnActionState.UNKNOWN
                && orderReturn.getActionState() != ReturnActionState.PROCESSING
                && orderReturn.getActionState() != ReturnActionState.FAILED) {
            throw new AppException(ErrorCode.ORDER_RETURN_ACTION_NOT_RETRYABLE);
        }
        return context(orderReturn);
    }

    @Transactional(readOnly = true)
    public ReturnActionContext contextForRefresh(UUID returnId) {
        OrderReturn orderReturn = returnRepository.findById(returnId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_RETURN_NOT_FOUND));
        return context(orderReturn);
    }

    @Transactional(readOnly = true)
    public ReturnActionContext contextForRejectOptions(UUID returnId) {
        OrderReturn orderReturn = returnRepository.findById(returnId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_RETURN_NOT_FOUND));
        validateAction(orderReturn, ReturnAction.REJECT);
        return context(orderReturn);
    }

    /** Đánh dấu action đã ổn định sau khi lưu snapshot platform. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markIdle(UUID returnId) {
        OrderReturn orderReturn = lock(returnId);
        orderReturn.setActionState(ReturnActionState.IDLE);
        orderReturn.setActionError(null);
        returnRepository.save(orderReturn);
    }

    /** Lưu lỗi từ chối chắc chắn của platform mà không đổi business status trả hàng. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID returnId, String error) {
        OrderReturn orderReturn = lock(returnId);
        orderReturn.setActionState(ReturnActionState.FAILED);
        orderReturn.setActionError(error);
        if (orderReturn.getLastAction() == ReturnAction.PROCESS
                && orderReturn.getStatus() == OrderReturnStatus.PLATFORM_PROCESSING) {
            orderReturn.setStatus(OrderReturnStatus.INSPECTED);
        }
        returnRepository.save(orderReturn);
    }

    /** Lưu kết quả chưa xác định để không thử lại platform một cách mù quáng. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markUnknown(UUID returnId, String error) {
        OrderReturn orderReturn = lock(returnId);
        orderReturn.setActionState(ReturnActionState.UNKNOWN);
        orderReturn.setActionError(error);
        returnRepository.save(orderReturn);
    }

    private OrderReturn lock(UUID id) {
        return returnRepository.findForUpdateById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_RETURN_NOT_FOUND));
    }

    private void validateAction(OrderReturn orderReturn, ReturnAction action) {
        if (orderReturn.getDataValidationState() != ReturnDataValidationState.VALID) {
            throw new AppException(ErrorCode.ORDER_RETURN_INVALID_STATE, "Return data is invalid");
        }
        boolean valid = switch (action) {
            case APPROVE, REJECT -> orderReturn.getStatus() == OrderReturnStatus.PENDING_APPROVAL;
            case PROCESS -> orderReturn.getStatus() == OrderReturnStatus.INSPECTED
                    || orderReturn.getStatus() == OrderReturnStatus.PLATFORM_PROCESSING;
        };
        if (!valid) throw new AppException(ErrorCode.ORDER_RETURN_INVALID_STATE);
    }

    private ReturnActionContext context(OrderReturn orderReturn) {
        List<ReturnActionContext.Item> items = itemRepository.findByReturnIdWithDetails(orderReturn.getId()).stream()
                .map(this::contextItem)
                .toList();
        return new ReturnActionContext(
                orderReturn.getId(),
                orderReturn.getChannel().getId(),
                orderReturn.getPlatform(),
                orderReturn.getExternalReturnId(),
                orderReturn.getLastAction(),
                orderReturn.getActionRequestId(),
                rejectCommand(orderReturn),
                items);
    }

    private void storeRejectCommand(OrderReturn orderReturn, ReturnRejectCommand command) {
        if (command == null) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Thiếu lý do từ chối");
        }
        Map<String, Object> metadata = orderReturn.getMetadata() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(orderReturn.getMetadata());
        Map<String, Object> payload = new LinkedHashMap<>();
        putIfText(payload, "reasonCode", command.reasonCode());
        putIfText(payload, "comment", command.comment());
        metadata.put("lastRejectAction", payload);
        orderReturn.setMetadata(metadata);
    }

    private ReturnRejectCommand rejectCommand(OrderReturn orderReturn) {
        if (orderReturn.getMetadata() == null) return null;
        Object raw = orderReturn.getMetadata().get("lastRejectAction");
        if (!(raw instanceof Map<?, ?> source)) return null;
        return new ReturnRejectCommand(text(source.get("reasonCode")), text(source.get("comment")));
    }

    private void putIfText(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) target.put(key, value.trim());
    }

    private String text(Object value) {
        return value == null || value.toString().isBlank() ? null : value.toString();
    }

    private ReturnActionContext.Item contextItem(OrderReturnItem item) {
        return new ReturnActionContext.Item(
                item.getExternalReturnItemId(),
                item.getApprovedQuantity(),
                item.getReceivedQuantity(),
                item.getRestockableQuantity(),
                item.getDamagedQuantity(),
                item.getMissingQuantity());
    }
}
