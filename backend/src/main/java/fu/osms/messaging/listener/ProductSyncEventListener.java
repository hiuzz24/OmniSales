package fu.osms.messaging.listener;

import fu.osms.messaging.constants.RabbitMQConstants;
import fu.osms.messaging.dto.ProductSyncMessage;
import fu.osms.sync.service.ProductSyncOrchestratorService;
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

    private final ProductSyncOrchestratorService productSyncOrchestratorService;

    @RabbitListener(queues = RabbitMQConstants.QUEUE_PRODUCT_PUSH, concurrency = "1-2")
    public void onProductSync(ProductSyncMessage message) {
        log.info("[ProductSyncEventListener] Syncing product productId={} channelId={}",
                message.productId(), message.channelId());
        if (message.channelId() == null) {
            productSyncOrchestratorService.syncProductToAllChannels(message.productId());
        } else {
            productSyncOrchestratorService.syncProductToChannel(message.productId(), message.channelId());
        }
    }
}
