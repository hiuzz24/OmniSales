package fu.osms.sync.webhook;

import fu.osms.sync.entity.WebhookEvent;
import fu.osms.sync.repository.WebhookEventRepository;
import fu.osms.sync.service.impl.WebhookEventProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Periodic sweeper that re-enqueues webhook events left stuck (processing died
 * mid-way or the async executor rejected them) and retries failed events up to
 * the configured limit. Deduplication stays safe because {@code webhook_events}
 * rows are claimed with {@code FOR UPDATE SKIP LOCKED}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookEventRetrySweeper {

    private final WebhookEventRepository webhookEventRepository;
    private final WebhookRetryProperties properties;
    private final WebhookEventProcessingService webhookEventProcessingService;

    @Scheduled(fixedDelayString = "${app.webhook-retry.fixed-delay-ms:30000}")
    @Transactional
    public void sweep() {
        if (!properties.isEnabled()) {
            return;
        }
        List<UUID> claimed = claimRetryableEvents();
        if (claimed.isEmpty()) {
            return;
        }
        log.info("[WebhookRetrySweeper] Re-enqueued {} stuck/failed webhook events", claimed.size());
        for (UUID eventId : claimed) {
            try {
                webhookEventProcessingService.processAsync(eventId);
            } catch (Exception e) {
                log.warn("[WebhookRetrySweeper] Failed to re-enqueue eventId={}", eventId, e);
            }
        }
    }

    public List<UUID> claimRetryableEvents() {
        OffsetDateTime stuckCutoff = OffsetDateTime.now().minusNanos(properties.getStuckAfterMs() * 1_000_000);
        List<UUID> ids = webhookEventRepository.claimRetryableWebhookEventIds(
                stuckCutoff, properties.getMaxRetries(), properties.getBatchSize());
        if (ids.isEmpty()) {
            return List.of();
        }
        OffsetDateTime now = OffsetDateTime.now();
        List<WebhookEvent> events = webhookEventRepository.findAllById(ids);
        for (WebhookEvent event : events) {
            event.setStatus("PROCESSING");
            event.setRetryCount((event.getRetryCount() == null ? 0 : event.getRetryCount()) + 1);
            event.setProcessedAt(now);
        }
        webhookEventRepository.saveAll(events);
        return ids;
    }
}
