package fu.osms.messaging.handler;

import fu.osms.messaging.dto.OrderReturnWorkflowMessage;
import fu.osms.orderreturn.enums.OrderReturnStatus;
import fu.osms.orderreturn.repository.OrderReturnRepository;
import fu.osms.orderreturn.service.OrderReturnInventoryPostingService;
import fu.osms.orderreturn.service.OrderReturnPaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderReturnWorkflowHandler {

    private final OrderReturnPaymentService paymentService;
    private final OrderReturnInventoryPostingService inventoryPostingService;
    private final OrderReturnRepository returnRepository;

    public void handle(OrderReturnWorkflowMessage message) {
        RuntimeException paymentFailure = null;
        try {
            paymentService.projectPayment(message.returnId());
        } catch (Exception exception) {
            log.error("[OrderReturnWorkflow] Payment projection failed returnId={} messageId={}",
                    message.returnId(), message.messageId(), exception);
            paymentFailure = asRuntime(exception);
        }

        if (!isPendingStock(message.returnId())) {
            try {
                inventoryPostingService.postIfReady(message.returnId());
            } catch (Exception stockFailure) {
                log.error("[OrderReturnWorkflow] Stock posting failed returnId={} messageId={}",
                        message.returnId(), message.messageId(), stockFailure);
                try {
                    inventoryPostingService.markPending(message.returnId(), rootMessage(stockFailure));
                } catch (Exception pendingFailure) {
                    log.error("[OrderReturnWorkflow] Mark pending failed returnId={} messageId={}",
                            message.returnId(), message.messageId(), pendingFailure);
                    RuntimeException retryable = paymentFailure != null ? paymentFailure : asRuntime(stockFailure);
                    retryable.addSuppressed(pendingFailure);
                    throw retryable;
                }
            }
        }

        if (paymentFailure != null) {
            throw paymentFailure;
        }
    }

    private boolean isPendingStock(java.util.UUID returnId) {
        return returnRepository.findById(returnId)
                .map(orderReturn -> orderReturn.getStatus() == OrderReturnStatus.PENDING_STOCK)
                .orElse(false);
    }

    private RuntimeException asRuntime(Exception exception) {
        return exception instanceof RuntimeException runtime
                ? runtime
                : new IllegalStateException(exception.getMessage(), exception);
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
