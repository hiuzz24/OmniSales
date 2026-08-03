package fu.osms.sync.shopify.impl;

import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelCredential;
import fu.osms.channel.repository.ChannelCredentialRepository;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.channel.service.ChannelConnectionLogService;
import fu.osms.common.enums.PlatformType;
import fu.osms.sync.shopify.ShopifyShopDomainNormalizer;
import fu.osms.sync.shopify.ShopifyWebhookSubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ShopifyDisconnectCleanupServiceImpl Tests")
class ShopifyDisconnectCleanupServiceImplTest {

    @Mock private ChannelRepository channelRepository;
    @Mock private ChannelCredentialRepository credentialRepository;
    @Mock private ShopifyShopDomainNormalizer shopDomainNormalizer;
    @Mock private ShopifyWebhookSubscriptionService webhookSubscriptionService;
    @Mock private ChannelConnectionLogService connectionLogService;
    @Mock private TransactionTemplate transactionTemplate;

    private ShopifyDisconnectCleanupServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ShopifyDisconnectCleanupServiceImpl(
                channelRepository, credentialRepository, shopDomainNormalizer,
                webhookSubscriptionService, connectionLogService, transactionTemplate);
    }

    private Channel channel(UUID id, PlatformType platform, String displayName, Map<String, Object> metadata) {
        return Channel.builder()
                .id(id)
                .platform(platform)
                .displayName(displayName)
                .metadata(metadata == null ? null : new HashMap<>(metadata))
                .build();
    }

    private ChannelCredential credential(String accessToken) {
        return ChannelCredential.builder().id(UUID.randomUUID()).accessToken(accessToken).build();
    }

    @Test
    @DisplayName("cleanup: no-op when channel does not exist")
    void cleanup_channelNotFound() {
        UUID id = UUID.randomUUID();
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            var callback = inv.getArgument(0, org.springframework.transaction.support.TransactionCallback.class);
            return callback.doInTransaction(null);
        });
        when(channelRepository.findById(id)).thenReturn(Optional.empty());

        service.cleanup(id);

        verify(webhookSubscriptionService, never()).unregisterWebhooks(any(), any(), any());
    }

    @Test
    @DisplayName("cleanup: no-op when channel is not Shopify")
    void cleanup_nonShopifyChannel() {
        UUID id = UUID.randomUUID();
        Channel ch = channel(id, PlatformType.LAZADA, "Lazada", null);
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            var callback = inv.getArgument(0, org.springframework.transaction.support.TransactionCallback.class);
            return callback.doInTransaction(null);
        });
        when(channelRepository.findById(id)).thenReturn(Optional.of(ch));

        service.cleanup(id);

        verify(webhookSubscriptionService, never()).unregisterWebhooks(any(), any(), any());
    }

    @Test
    @DisplayName("cleanup: no-op when credentials are missing or empty")
    void cleanup_noCredentials() {
        UUID id = UUID.randomUUID();
        Channel ch = channel(id, PlatformType.SHOPIFY, "Shop", null);
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            var callback = inv.getArgument(0, org.springframework.transaction.support.TransactionCallback.class);
            return callback.doInTransaction(null);
        });
        when(channelRepository.findById(id)).thenReturn(Optional.of(ch));
        when(credentialRepository.findByChannelId(id)).thenReturn(Optional.empty());

        service.cleanup(id);

        verify(webhookSubscriptionService, never()).unregisterWebhooks(any(), any(), any());
    }

    @Test
    @DisplayName("cleanup: happy path unregisters webhooks using stored shopDomain + access token")
    void cleanup_happyPath() {
        UUID id = UUID.randomUUID();
        Map<String, Object> metadata = new HashMap<>(Map.of(
                "shopDomain", "test-shop.myshopify.com",
                "shopifyWebhooks", List.of(
                        Map.of("id", "wh-1", "topic", "orders/create"),
                        Map.of("id", "wh-2", "topic", "inventory_levels/update"))));
        Channel ch = channel(id, PlatformType.SHOPIFY, "Test Shop", metadata);
        ChannelCredential cred = credential("shpat-xxx");

        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            var callback = inv.getArgument(0, org.springframework.transaction.support.TransactionCallback.class);
            return callback.doInTransaction(null);
        });
        when(channelRepository.findById(id)).thenReturn(Optional.of(ch));
        when(credentialRepository.findByChannelId(id)).thenReturn(Optional.of(cred));
        when(shopDomainNormalizer.normalizeHandle("test-shop.myshopify.com")).thenReturn("test-shop");

        service.cleanup(id);

        verify(webhookSubscriptionService).unregisterWebhooks(
                eq("test-shop"), eq("shpat-xxx"),
                eq(List.of(
                        Map.of("id", "wh-1", "topic", "orders/create"),
                        Map.of("id", "wh-2", "topic", "inventory_levels/update"))));
    }

    @Test
    @DisplayName("cleanup: falls back to displayName for shop handle when shopDomain metadata is missing")
    void cleanup_fallbackToDisplayName() {
        UUID id = UUID.randomUUID();
        Channel ch = channel(id, PlatformType.SHOPIFY, "My Display Name", null);
        ChannelCredential cred = credential("tok");

        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            var callback = inv.getArgument(0, org.springframework.transaction.support.TransactionCallback.class);
            return callback.doInTransaction(null);
        });
        when(channelRepository.findById(id)).thenReturn(Optional.of(ch));
        when(credentialRepository.findByChannelId(id)).thenReturn(Optional.of(cred));
        when(shopDomainNormalizer.normalizeHandle("My Display Name")).thenReturn("my-display-name");

        service.cleanup(id);

        verify(shopDomainNormalizer).normalizeHandle("My Display Name");
        verify(webhookSubscriptionService).unregisterWebhooks(
                eq("my-display-name"), eq("tok"), eq(List.of()));
    }

    @Test
    @DisplayName("cleanup: empty webhook list when metadata.shopifyWebhooks is not a list")
    void cleanup_webhooksNotAList() {
        UUID id = UUID.randomUUID();
        Map<String, Object> metadata = new HashMap<>(Map.of(
                "shopDomain", "test-shop.myshopify.com",
                "shopifyWebhooks", "not-a-list"));
        Channel ch = channel(id, PlatformType.SHOPIFY, "Test Shop", metadata);
        ChannelCredential cred = credential("tok");

        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            var callback = inv.getArgument(0, org.springframework.transaction.support.TransactionCallback.class);
            return callback.doInTransaction(null);
        });
        when(channelRepository.findById(id)).thenReturn(Optional.of(ch));
        when(credentialRepository.findByChannelId(id)).thenReturn(Optional.of(cred));
        when(shopDomainNormalizer.normalizeHandle("test-shop.myshopify.com")).thenReturn("test-shop");

        service.cleanup(id);

        verify(webhookSubscriptionService).unregisterWebhooks(eq("test-shop"), eq("tok"), eq(List.of()));
    }

    @Test
    @DisplayName("cleanup: logs a connection failure when webhook unregister throws")
    void cleanup_webhookThrows() {
        UUID id = UUID.randomUUID();
        Map<String, Object> metadata = new HashMap<>(Map.of("shopDomain", "shop.myshopify.com"));
        Channel ch = channel(id, PlatformType.SHOPIFY, "My Shop", metadata);
        ChannelCredential cred = credential("tok");

        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            var callback = inv.getArgument(0, org.springframework.transaction.support.TransactionCallback.class);
            return callback.doInTransaction(null);
        });
        when(channelRepository.findById(id)).thenReturn(Optional.of(ch));
        when(credentialRepository.findByChannelId(id)).thenReturn(Optional.of(cred));
        when(shopDomainNormalizer.normalizeHandle("shop.myshopify.com")).thenReturn("shop");
        doThrow(new RuntimeException("network error"))
                .when(webhookSubscriptionService).unregisterWebhooks(eq("shop"), eq("tok"), eq(List.of()));

        service.cleanup(id);

        verify(connectionLogService).logFailure(
                eq(PlatformType.SHOPIFY),
                eq(fu.osms.channel.enums.ChannelConnectionAction.DISCONNECT),
                any(),
                eq("network error"),
                any());
    }

    @Test
    @DisplayName("cleanup: no-op when transactionTemplate.execute returns null")
    void cleanup_txReturnsNull() {
        UUID id = UUID.randomUUID();
        when(transactionTemplate.execute(any())).thenReturn(null);

        service.cleanup(id);

        verify(channelRepository, never()).findById(any());
        verify(webhookSubscriptionService, never()).unregisterWebhooks(any(), any(), any());
    }

    @Test
    @DisplayName("cleanup: uses empty webhooks list when metadata is null")
    void cleanup_nullMetadata() {
        UUID id = UUID.randomUUID();
        Channel ch = channel(id, PlatformType.SHOPIFY, "Shop", null);
        ChannelCredential cred = credential("tok");

        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            var callback = inv.getArgument(0, org.springframework.transaction.support.TransactionCallback.class);
            return callback.doInTransaction(null);
        });
        when(channelRepository.findById(id)).thenReturn(Optional.of(ch));
        when(credentialRepository.findByChannelId(id)).thenReturn(Optional.of(cred));
        when(shopDomainNormalizer.normalizeHandle("Shop")).thenReturn("shop");

        service.cleanup(id);

        verify(webhookSubscriptionService).unregisterWebhooks(eq("shop"), eq("tok"), eq(List.of()));
    }
}
