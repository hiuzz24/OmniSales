package fu.osms.messaging.publisher;

import fu.osms.messaging.constants.RabbitMQConstants;
import fu.osms.messaging.properties.MessagingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes sync events to the {@code osms.topic.exchange}.
 * <p>
 * In {@code local} mode events are dispatched in-process instead of being
 * published. In {@code broker} mode a broker outage is tolerated: the optional
 * local fallback runs so events are never silently dropped.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final MessagingProperties messagingProperties;

    public void publish(String routingKey, Object payload) {
        publish(routingKey, payload, null);
    }

    public void publish(String routingKey, Object payload, Runnable localFallback) {
        if (!messagingProperties.isBrokerMode()) {
            if (localFallback != null) {
                runFallback(localFallback);
            }
            return;
        }
        try {
            rabbitTemplate.convertAndSend(RabbitMQConstants.TOPIC_EXCHANGE, routingKey, payload);
        } catch (AmqpException e) {
            log.warn("[EventPublisher] RabbitMQ unavailable, using local fallback routingKey={}", routingKey, e);
            if (localFallback != null) {
                runFallback(localFallback);
            }
        }
    }

    private void runFallback(Runnable localFallback) {
        try {
            localFallback.run();
        } catch (Exception e) {
            log.error("[EventPublisher] Local fallback failed", e);
        }
    }
}
