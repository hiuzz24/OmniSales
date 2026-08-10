package fu.osms.sync.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.channel.entity.Channel;
import fu.osms.common.dto.PageResponse;
import fu.osms.common.enums.PlatformType;
import fu.osms.sync.dto.WebhookEventResponse;
import fu.osms.sync.dto.WebhookReceiveResult;
import fu.osms.sync.entity.WebhookEvent;
import fu.osms.sync.mapper.WebhookEventMapper;
import fu.osms.sync.repository.WebhookEventRepository;
import fu.osms.sync.webhook.PlatformWebhookHandler;
import fu.osms.messaging.publisher.EventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebhookReceiverServiceImpl - Unit Tests")
class WebhookReceiverServiceImplTest {

    private WebhookReceiverServiceImpl webhookReceiverService;

    @Mock
    private PlatformWebhookHandler shopifyHandler;

    @Mock
    private WebhookEventRepository webhookEventRepository;

    @Mock
    private WebhookEventMapper webhookEventMapper;

    @Mock
    private WebhookEventProcessingService webhookEventProcessingService;

    @Mock
    private EventPublisher eventPublisher;

    private ObjectMapper objectMapper;

    private UUID eventId;
    private UUID channelId;
    private Channel sampleChannel;
    private WebhookEvent sampleEvent;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        // Use only one handler to avoid duplicate key exception
        webhookReceiverService = new WebhookReceiverServiceImpl(
                List.of(shopifyHandler),
                webhookEventRepository,
                webhookEventMapper,
                webhookEventProcessingService,
                eventPublisher,
                objectMapper
        );

        eventId = UUID.randomUUID();
        channelId = UUID.randomUUID();

        sampleChannel = Channel.builder()
                .id(channelId)
                .platform(PlatformType.SHOPIFY)
                .displayName("Test Shop")
                .build();

        sampleEvent = WebhookEvent.builder()
                .id(eventId)
                .platform(PlatformType.SHOPIFY)
                .channel(sampleChannel)
                .eventType("orders/create")
                .externalEventId("ext-123")
                .status("RECEIVED")
                .build();
    }

    @Test
    @DisplayName("receive - unsupported platform throws exception")
    void receive_unsupportedPlatform() {
        Map<String, String> headers = Map.of("X-Shopify-Topic", "orders/create");
        String rawBody = "{\"test\": \"data\"}";

        assertThatThrownBy(() -> webhookReceiverService.receive(PlatformType.LAZADA, headers, rawBody))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Unsupported webhook platform");
    }

    @Test
    @DisplayName("receive - invalid signature throws exception")
    void receive_invalidSignature() {
        Map<String, String> headers = Map.of("X-Shopify-Topic", "orders/create");
        String rawBody = "{\"test\": \"data\"}";

        when(shopifyHandler.getPlatform()).thenReturn(PlatformType.SHOPIFY);
        when(shopifyHandler.verify(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> webhookReceiverService.receive(PlatformType.SHOPIFY, headers, rawBody))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid webhook signature");
    }

    @Test
    @DisplayName("receive - duplicate webhook returns ignored result")
    void receive_duplicateWebhook() {
        Map<String, String> headers = Map.of("X-Shopify-Shop-Domain", "test.myshopify.com");
        String rawBody = "{\"id\": 123}";

        when(shopifyHandler.getPlatform()).thenReturn(PlatformType.SHOPIFY);
        when(shopifyHandler.verify(any(), any())).thenReturn(true);
        when(shopifyHandler.extractEventType(any(), any())).thenReturn("orders/create");
        when(shopifyHandler.extractExternalEventId(any(), any(), any())).thenReturn("ext-123");
        // Note: resolveChannel is NOT called for duplicate webhooks (code returns early)
        when(webhookEventRepository.findByPlatformAndExternalEventId(PlatformType.SHOPIFY, "ext-123"))
                .thenReturn(Optional.of(sampleEvent));

        WebhookReceiveResult result = webhookReceiverService.receive(PlatformType.SHOPIFY, headers, rawBody);

        assertThat(result.getMessage()).isEqualTo("Duplicate webhook ignored");
        verify(webhookEventRepository, never()).save(any());
        verify(webhookEventRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("receive - shopify queues async processing via TransactionSynchronization fallback")
    void receive_shopify_success() {
        Map<String, String> headers = Map.of("X-Shopify-Shop-Domain", "test.myshopify.com");
        String rawBody = "{\"id\": 123}";

        when(shopifyHandler.getPlatform()).thenReturn(PlatformType.SHOPIFY);
        when(shopifyHandler.verify(any(), any())).thenReturn(true);
        when(shopifyHandler.extractEventType(any(), any())).thenReturn("orders/create");
        when(shopifyHandler.extractExternalEventId(any(), any(), any())).thenReturn("ext-123");
        when(shopifyHandler.resolveChannel(any(), any())).thenReturn(Optional.of(sampleChannel));
        when(webhookEventRepository.findByPlatformAndExternalEventId(any(), any())).thenReturn(Optional.empty());
        when(webhookEventRepository.saveAndFlush(any(WebhookEvent.class))).thenAnswer(inv -> {
            WebhookEvent e = inv.getArgument(0);
            e.setId(eventId);
            return e;
        });
        // Simulate local fallback: the publisher runs the in-process async dispatch
        doAnswer(inv -> {
            Runnable fallback = inv.getArgument(2);
            fallback.run();
            return null;
        }).when(eventPublisher).publish(any(), any(), any());

        WebhookReceiveResult result = webhookReceiverService.receive(PlatformType.SHOPIFY, headers, rawBody);

        // Outside a real transaction the service falls back to a synchronous
        // processAsync call so the work is still triggered from the unit test.
        assertThat(result.getStatus()).isEqualTo("RECEIVED");
        assertThat(result.getMessage()).isEqualTo("Webhook queued");
        verify(webhookEventProcessingService).processAsync(eventId);
    }

    @Test
    @DisplayName("receive - lazada processes asynchronously")
    void receive_lazada_async() {
        Map<String, String> headers = Map.of();
        String rawBody = "{\"order_id\": 123}";

        // Create a separate service instance with LAZADA handler
        PlatformWebhookHandler lazadaHandler = mock(PlatformWebhookHandler.class);
        WebhookReceiverServiceImpl lazadaService = new WebhookReceiverServiceImpl(
                List.of(lazadaHandler),
                webhookEventRepository,
                webhookEventMapper,
                webhookEventProcessingService,
                eventPublisher,
                objectMapper
        );

        when(lazadaHandler.getPlatform()).thenReturn(PlatformType.LAZADA);
        when(lazadaHandler.verify(any(), any())).thenReturn(true);
        when(lazadaHandler.extractEventType(any(), any())).thenReturn("order_created");
        when(lazadaHandler.extractExternalEventId(any(), any(), any())).thenReturn("laz-123");
        when(lazadaHandler.resolveChannel(any(), any())).thenReturn(Optional.of(sampleChannel));
        when(webhookEventRepository.findByPlatformAndExternalEventId(any(), any())).thenReturn(Optional.empty());
        when(webhookEventRepository.saveAndFlush(any(WebhookEvent.class))).thenAnswer(inv -> {
            WebhookEvent e = inv.getArgument(0);
            e.setId(eventId);
            return e;
        });

        WebhookReceiveResult result = lazadaService.receive(PlatformType.LAZADA, headers, rawBody);

        assertThat(result.getMessage()).isEqualTo("Webhook queued");
        verify(webhookEventProcessingService, never()).processSavedEvent(any());
    }

    @Test
    @DisplayName("receive - channel not resolved sets FAILED status")
    void receive_channelNotResolved() {
        Map<String, String> headers = Map.of();
        String rawBody = "{\"id\": 123}";

        when(shopifyHandler.getPlatform()).thenReturn(PlatformType.SHOPIFY);
        when(shopifyHandler.verify(any(), any())).thenReturn(true);
        when(shopifyHandler.extractEventType(any(), any())).thenReturn("orders/create");
        when(shopifyHandler.extractExternalEventId(any(), any(), any())).thenReturn("ext-123");
        when(shopifyHandler.resolveChannel(any(), any())).thenReturn(Optional.empty());
        when(webhookEventRepository.findByPlatformAndExternalEventId(any(), any())).thenReturn(Optional.empty());
        when(webhookEventRepository.saveAndFlush(any(WebhookEvent.class))).thenAnswer(inv -> {
            WebhookEvent e = inv.getArgument(0);
            e.setId(eventId);
            return e;
        });
        when(webhookEventRepository.save(any(WebhookEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        WebhookReceiveResult result = webhookReceiverService.receive(PlatformType.SHOPIFY, headers, rawBody);

        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getMessage()).isEqualTo("Cannot resolve channel for webhook");
    }

    @Test
    @DisplayName("search - returns paginated events")
    void search_returnsPaginatedEvents() {
        WebhookEventResponse response = WebhookEventResponse.builder()
                .id(eventId)
                .platform(PlatformType.SHOPIFY)
                .eventType("orders/create")
                .build();

        Page<WebhookEvent> page = new PageImpl<>(List.of(sampleEvent));

        when(webhookEventRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(webhookEventMapper.toResponse(any(WebhookEvent.class))).thenReturn(response);

        PageResponse<WebhookEventResponse> result = webhookReceiverService.search(null, null, null, null, 0, 10);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("search - with filters")
    void search_withFilters() {
        WebhookEventResponse response = WebhookEventResponse.builder()
                .id(eventId)
                .platform(PlatformType.SHOPIFY)
                .eventType("orders/create")
                .status("COMPLETED")
                .build();

        Page<WebhookEvent> page = new PageImpl<>(List.of(sampleEvent));

        when(webhookEventRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(webhookEventMapper.toResponse(any(WebhookEvent.class))).thenReturn(response);

        PageResponse<WebhookEventResponse> result = webhookReceiverService.search(
                PlatformType.SHOPIFY, "COMPLETED", "orders/create", channelId, 0, 10
        );

        assertThat(result.getContent()).hasSize(1);
        verify(webhookEventRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("search - returns empty when no results")
    void search_returnsEmptyWhenNoResults() {
        Page<WebhookEvent> emptyPage = new PageImpl<>(List.of());

        when(webhookEventRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(emptyPage);

        PageResponse<WebhookEventResponse> result = webhookReceiverService.search(null, null, null, null, 0, 10);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(0);
    }

    @Test
    @DisplayName("parsePayload - invalid JSON throws exception")
    void parsePayload_invalidJson() {
        String invalidJson = "not valid json {{{{";

        when(shopifyHandler.getPlatform()).thenReturn(PlatformType.SHOPIFY);
        when(shopifyHandler.verify(any(), any())).thenReturn(true);

        assertThatThrownBy(() -> webhookReceiverService.receive(PlatformType.SHOPIFY, Map.of(), invalidJson))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid webhook JSON payload");
    }
}
