package fu.osms.orderreturn.listener;

import fu.osms.orderreturn.event.OrderReturnChangedEvent;
import fu.osms.messaging.constants.RabbitMQConstants;
import fu.osms.messaging.dto.OrderReturnWorkflowMessage;
import fu.osms.messaging.handler.OrderReturnWorkflowHandler;
import fu.osms.messaging.publisher.EventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OrderReturnWorkflowListener {

    private final EventPublisher eventPublisher;
    private final OrderReturnWorkflowHandler handler;

    /** Phát tác vụ payment/restock sau commit qua chế độ messaging đã cấu hình. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChanged(OrderReturnChangedEvent event) {
        OrderReturnWorkflowMessage message = new OrderReturnWorkflowMessage(
                UUID.randomUUID(), event.returnId());
        eventPublisher.publish(
                RabbitMQConstants.ORDER_RETURN_WORKFLOW,
                message,
                () -> handler.handle(message));
    }
}
