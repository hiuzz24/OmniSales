package fu.osms.messaging.listener;

import fu.osms.messaging.constants.RabbitMQConstants;
import fu.osms.messaging.dto.WebhookEventMessage;
import fu.osms.sync.service.impl.WebhookEventProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumes persisted webhook event ids and processes them. Redelivery is safe:
 * the {@code uq_webhook_dedup} unique index on webhook_events makes the whole
 * flow idempotent.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookEventListener {

    private final WebhookEventProcessingService webhookEventProcessingService;

    @RabbitListener(queues = RabbitMQConstants.QUEUE_WEBHOOK_SYNC_ORDER, concurrency = "2-4")
    public void onOrderEvent(WebhookEventMessage message) {
        process(message);
    }

    @RabbitListener(queues = RabbitMQConstants.QUEUE_WEBHOOK_SYNC_PRODUCT, concurrency = "2-4")
    public void onProductEvent(WebhookEventMessage message) {
        process(message);
    }

    private void process(WebhookEventMessage message) {
        try {
            webhookEventProcessingService.processSavedEvent(message.eventId());
        } catch (Exception e) {
            log.error("[WebhookEventListener] Processing failed eventId={}", message.eventId(), e);
        }
    }
}
