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
import fu.osms.orderreturn.repository.OrderReturnItemRepository;
import fu.osms.orderreturn.repository.OrderReturnRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderReturnActionStateService {

    private final OrderReturnRepository returnRepository;
    private final OrderReturnItemRepository itemRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ReturnActionContext beginNew(UUID returnId, ReturnAction action) {
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
        if (action == ReturnAction.PROCESS) {
            orderReturn.setStatus(OrderReturnStatus.PLATFORM_PROCESSING);
        }
        returnRepository.save(orderReturn);
        return context(orderReturn);
    }

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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markIdle(UUID returnId) {
        OrderReturn orderReturn = lock(returnId);
        orderReturn.setActionState(ReturnActionState.IDLE);
        orderReturn.setActionError(null);
        returnRepository.save(orderReturn);
    }

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
                items);
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
