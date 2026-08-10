package fu.osms.messaging.listener;

import fu.osms.messaging.constants.RabbitMQConstants;
import fu.osms.messaging.dto.OrderStockDeliveryLifecycleMessage;
import fu.osms.messaging.handler.OrderStockDeliveryLifecycleHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderStockDeliveryLifecycleListener {

    private final OrderStockDeliveryLifecycleHandler handler;

    @RabbitListener(queues = RabbitMQConstants.QUEUE_ORDER_STOCK_DELIVERY_LIFECYCLE, concurrency = "1-2")
    public void onLifecycle(OrderStockDeliveryLifecycleMessage message) {
        handler.handle(message);
    }
}
