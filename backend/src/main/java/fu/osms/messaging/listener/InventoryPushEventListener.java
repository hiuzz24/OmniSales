package fu.osms.messaging.listener;

import fu.osms.messaging.constants.RabbitMQConstants;
import fu.osms.messaging.dto.InventoryPushMessage;
import fu.osms.sync.service.MarketplaceInventoryPropagationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Pushes available stock to marketplaces. Failures are allowed to propagate so
 * Spring AMQP retries with backoff and dead-letters to the DLQ after
 * max-attempts; pushes are idempotent (they set the same quantity), so retry is
 * safe.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryPushEventListener {

    private final MarketplaceInventoryPropagationService marketplaceInventoryPropagationService;

    @RabbitListener(queues = RabbitMQConstants.QUEUE_INVENTORY_PUSH, concurrency = "1-2")
    public void onInventoryPush(InventoryPushMessage message) {
        log.info("[InventoryPushEventListener] Pushing stock variantIds={} excludedChannelId={}",
                message.variantIds(), message.excludedChannelId());
        marketplaceInventoryPropagationService.pushAvailableStock(
                message.variantIds(), message.excludedChannelId());
    }
}
