package fu.osms.messaging.listener;

import fu.osms.messaging.constants.RabbitMQConstants;
import fu.osms.messaging.dto.ProductSyncMessage;
import fu.osms.messaging.handler.ProductSyncRequestHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Pushes a product to its connected marketplaces in the background. Failures
 * propagate so Spring AMQP retries and dead-letters to the DLQ.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductSyncEventListener {

    private final ProductSyncRequestHandler handler;

    @RabbitListener(queues = RabbitMQConstants.QUEUE_PRODUCT_PUSH, concurrency = "1-2")
    public void onProductSync(ProductSyncMessage message) {
        log.info("[ProductSyncEventListener] requestLogId={} productId={} channelId={}",
                message.requestLogId(), message.productId(), message.channelId());
        handler.handle(message);
    }
}
