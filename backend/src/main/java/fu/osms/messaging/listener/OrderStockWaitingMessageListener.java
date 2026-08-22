package fu.osms.messaging.listener;

import fu.osms.messaging.constants.RabbitMQConstants;
import fu.osms.messaging.dto.OrderStockWaitingMessage;
import fu.osms.messaging.handler.OrderStockWaitingHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderStockWaitingMessageListener {
    private final OrderStockWaitingHandler handler;

    @RabbitListener(
            queues = RabbitMQConstants.QUEUE_ORDER_STOCK_WAITING_RECONCILE,
            concurrency = "1",
            containerFactory = "orderStockWaitingContainerFactory")
    public void consume(OrderStockWaitingMessage message) {
        handler.handle(message);
    }
}
