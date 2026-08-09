package fu.osms.orderreturn.listener;

import fu.osms.orderreturn.event.OrderReturnChangedEvent;
import fu.osms.messaging.constants.RabbitMQConstants;
import fu.osms.messaging.dto.OrderReturnWorkflowMessage;
import fu.osms.messaging.handler.OrderReturnWorkflowHandler;
import fu.osms.messaging.publisher.EventPublisher;
import fu.osms.orderreturn.event.OrderReturnProcessedEvent;
import fu.osms.orderreturn.service.OrderReturnInventoryPostingService;
import fu.osms.orderreturn.service.OrderReturnPaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OrderReturnWorkflowListener {

    private final EventPublisher eventPublisher;
    private final OrderReturnWorkflowHandler handler;
    private final OrderReturnPaymentService paymentService;
    private final OrderReturnInventoryPostingService inventoryPostingService;
    private final ApplicationEventPublisher eventPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChanged(OrderReturnChangedEvent event) {
        OrderReturnWorkflowMessage message = new OrderReturnWorkflowMessage(
                UUID.randomUUID(), event.returnId());
        eventPublisher.publish(
                RabbitMQConstants.ORDER_RETURN_WORKFLOW,
                message,
                () -> handler.handle(message));
        try {
            paymentService.projectPayment(event.returnId());
        } catch (Exception exception) {
            log.error("[OrderReturn] Failed to project payment returnId={}", event.returnId(), exception);
        }
        try {
            inventoryPostingService.postIfReady(event.returnId());
        } catch (Exception exception) {
            log.error("[OrderReturn] Failed to post return stock returnId={}", event.returnId(), exception);
            try {
                inventoryPostingService.markPending(event.returnId(), rootMessage(exception));
            } catch (Exception pendingException) {
                log.error("[OrderReturn] Failed to mark pending stock returnId={}",
                        event.returnId(), pendingException);
            }
        }
        eventPublisher.publishEvent(new OrderReturnProcessedEvent(event.returnId()));
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
