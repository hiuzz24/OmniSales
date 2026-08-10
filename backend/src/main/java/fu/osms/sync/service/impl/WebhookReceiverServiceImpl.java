package fu.osms.sync.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.channel.entity.Channel;
import fu.osms.common.dto.PageResponse;
import fu.osms.common.enums.PlatformType;
import fu.osms.messaging.constants.RabbitMQConstants;
import fu.osms.messaging.dto.WebhookEventMessage;
import fu.osms.messaging.publisher.EventPublisher;
import fu.osms.sync.dto.WebhookEventResponse;
import fu.osms.sync.dto.WebhookReceiveResult;
import fu.osms.sync.entity.WebhookEvent;
import fu.osms.sync.mapper.WebhookEventMapper;
import fu.osms.sync.repository.WebhookEventRepository;
import fu.osms.sync.service.WebhookReceiverService;
import fu.osms.sync.webhook.PlatformWebhookHandler;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookReceiverServiceImpl implements WebhookReceiverService {

    private final List<PlatformWebhookHandler> handlers;
    private final WebhookEventRepository webhookEventRepository;
    private final WebhookEventMapper webhookEventMapper;
    private final WebhookEventProcessingService webhookEventProcessingService;
    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public WebhookReceiveResult receive(PlatformType platform, Map<String, String> headers, String rawBody) {
        PlatformWebhookHandler handler = handlerMap().get(platform);
        if (handler == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported webhook platform");
        }
        if (!handler.verify(headers, rawBody)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid webhook signature");
        }

        Map<String, Object> payload = parsePayload(rawBody);
        if (handler.shouldIgnore(payload)) {
            return WebhookReceiveResult.builder()
                    .status("IGNORED")
                    .message("Webhook ignored by handler rule")
                    .build();
        }

        String eventType = handler.extractEventType(headers, payload);
        String externalEventId = handler.extractExternalEventId(headers, payload, rawBody);
        WebhookEvent existing = findExisting(platform, externalEventId);
        if (existing != null) {
            return duplicateResult(existing);
        }

        Channel channel = handler.resolveChannel(headers, payload).orElse(null);
        WebhookEvent event = WebhookEvent.builder()
                .platform(platform)
                .channel(channel)
                .eventType(eventType)
                .externalEventId(externalEventId)
                .status("RECEIVED")
                .rawPayload(payload)
                .build();

        try {
            event = webhookEventRepository.saveAndFlush(event);
        } catch (DataIntegrityViolationException e) {
            if (externalEventId != null) {
                return duplicateResult(platform, externalEventId);
            }
            throw e;
        }

        log.info("[WebhookReceiver] Persisted eventId={}, platform={}, eventType={}, channelId={}",
                event.getId(), platform, eventType, channel == null ? null : channel.getId());

        if (channel == null) {
            event.setStatus("FAILED");
            event.setErrorMessage("Cannot resolve channel for webhook");
            event.setProcessedAt(OffsetDateTime.now());
            webhookEventRepository.save(event);
            return WebhookReceiveResult.builder()
                    .webhookEventId(event.getId())
                    .status(event.getStatus())
                    .message(event.getErrorMessage())
                    .build();
        }

        processAsyncAfterCommit(event.getId(), eventType);
        return WebhookReceiveResult.builder()
                .webhookEventId(event.getId())
                .status(event.getStatus())
                .message("Webhook queued")
                .build();
    }

    private void processAsyncAfterCommit(UUID eventId, String eventType) {
        Runnable dispatch = () -> eventPublisher.publish(
                routingKeyFor(eventType),
                new WebhookEventMessage(eventId),
                () -> webhookEventProcessingService.processAsync(eventId));
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            dispatch.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                dispatch.run();
            }
        });
    }

    private String routingKeyFor(String eventType) {
        String type = eventType == null ? "" : eventType.toUpperCase();
        if (type.contains("ORDER") || type.contains("TRADE")
                || type.contains("RETURN") || type.contains("REVERSE")) {
            return RabbitMQConstants.SYNC_ORDER;
        }
        return RabbitMQConstants.SYNC_PRODUCT;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<WebhookEventResponse> search(PlatformType platform,
                                                     String status,
                                                     String eventType,
                                                     UUID channelId,
                                                     int page,
                                                     int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "receivedAt"));
        Specification<WebhookEvent> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (platform != null) predicates.add(cb.equal(root.get("platform"), platform));
            if (status != null && !status.isBlank()) predicates.add(cb.equal(root.get("status"), status));
            if (eventType != null && !eventType.isBlank()) predicates.add(cb.equal(root.get("eventType"), eventType));
            if (channelId != null) predicates.add(cb.equal(root.get("channel").get("id"), channelId));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<WebhookEvent> result = webhookEventRepository.findAll(spec, pageable);
        return PageResponse.<WebhookEventResponse>builder()
                .content(result.getContent().stream().map(webhookEventMapper::toResponse).toList())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .first(result.isFirst())
                .last(result.isLast())
                .build();
    }

    private Map<PlatformType, PlatformWebhookHandler> handlerMap() {
        return handlers.stream().collect(Collectors.toMap(PlatformWebhookHandler::getPlatform, Function.identity()));
    }

    private WebhookEvent findExisting(PlatformType platform, String externalEventId) {
        if (externalEventId == null) {
            return null;
        }
        return webhookEventRepository.findByPlatformAndExternalEventId(platform, externalEventId).orElse(null);
    }

    private WebhookReceiveResult duplicateResult(WebhookEvent existing) {
        return WebhookReceiveResult.builder()
                .webhookEventId(existing.getId())
                .status(existing.getStatus())
                .message("Duplicate webhook ignored")
                .build();
    }

    private WebhookReceiveResult duplicateResult(PlatformType platform, String externalEventId) {
        return webhookEventRepository.findByPlatformAndExternalEventId(platform, externalEventId)
                .map(this::duplicateResult)
                .orElseGet(() -> WebhookReceiveResult.builder()
                        .status("IGNORED")
                        .message("Duplicate webhook ignored")
                        .build());
    }

    private Map<String, Object> parsePayload(String rawBody) {
        try {
            return objectMapper.readValue(rawBody, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid webhook JSON payload", e);
        }
    }
}
