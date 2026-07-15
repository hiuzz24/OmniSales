package fu.osms.sync.service.impl;

import fu.osms.sync.entity.WebhookEvent;
import fu.osms.sync.repository.WebhookEventRepository;
import fu.osms.sync.service.WebhookBusinessProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookEventProcessingService {

    private final WebhookEventRepository webhookEventRepository;
    private final WebhookBusinessProcessor webhookBusinessProcessor;

    @Async("webhookExecutor")
    @Transactional
    public void processAsync(UUID eventId) {
        try {
            processSavedEvent(eventId);
        } catch (Exception e) {
            log.error("[WebhookProcessor] Async processing failed eventId={}", eventId, e);
        }
    }

    @Transactional
    public WebhookEvent processSavedEvent(UUID eventId) {
        WebhookEvent event = webhookEventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Webhook event not found: " + eventId));
        try {
            String resultStatus = webhookBusinessProcessor.process(event);
            event.setStatus(resultStatus);
            event.setProcessedAt(OffsetDateTime.now());
            event.setErrorMessage(null);
        } catch (Exception e) {
            event.setStatus("FAILED");
            event.setErrorMessage(e.getMessage());
            event.setProcessedAt(OffsetDateTime.now());
        }
        return webhookEventRepository.save(event);
    }
}
