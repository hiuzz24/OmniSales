package fu.osms.messaging.listener;

import fu.osms.messaging.constants.RabbitMQConstants;
import fu.osms.messaging.dto.OrderReturnWorkflowMessage;
import fu.osms.messaging.handler.OrderReturnWorkflowHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderReturnWorkflowMessageListener {

    private final OrderReturnWorkflowHandler handler;

    @RabbitListener(queues = RabbitMQConstants.QUEUE_ORDER_RETURN_WORKFLOW, concurrency = "1-2")
    public void onWorkflow(OrderReturnWorkflowMessage message) {
        handler.handle(message);
    }
}
