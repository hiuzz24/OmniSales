package fu.osms.channel.service.impl;

import fu.osms.channel.dto.response.ChannelResponse;
import fu.osms.channel.entity.Channel;
import fu.osms.channel.enums.ChannelConnectionAction;
import fu.osms.channel.event.ChannelDisconnectedEvent;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.channel.service.ChannelConnectionLogService;
import fu.osms.channel.service.ChannelConnectionService;
import fu.osms.channel.service.ChannelCredentialLifecycleService;
import fu.osms.channel.service.ChannelMappingLifecycleService;
import fu.osms.channel.service.ChannelReconnectResolver;
import fu.osms.channel.service.ChannelResponseService;
import fu.osms.channel.service.model.MappingRestoreResult;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.sync.dto.shopify.WebhookRegistrationResult;
import fu.osms.sync.shopify.ShopifyShopDomainNormalizer;
import fu.osms.sync.shopify.ShopifyWebhookSubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelConnectionServiceImpl implements ChannelConnectionService {

    private final ChannelRepository channelRepository;
    private final ChannelReconnectResolver reconnectResolver;
    private final ChannelCredentialLifecycleService credentialLifecycleService;
    private final ChannelMappingLifecycleService mappingLifecycleService;
    private final ChannelConnectionLogService connectionLogService;
    private final ChannelResponseService responseService;
    private final ShopifyShopDomainNormalizer shopDomainNormalizer;
    private final ShopifyWebhookSubscriptionService shopifyWebhookSubscriptionService;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${lazada.webhook-callback-url:}")
    private String lazadaWebhookCallbackUrl;

    @Override
    @Transactional
    public ChannelResponse connectShopify(String shop, String accessToken) {
        String handle = shopDomainNormalizer.normalizeHandle(shop);
        Channel channel = reconnectResolver.resolveShopify(handle);
        boolean restoring = isSoftDeleted(channel);
        ChannelConnectionAction action = connectionAction(channel);

        Map<String, Object> metadata = mutableMetadata(channel);
        metadata.put("shopDomain", handle);
        activate(channel, handle, metadata);
        channelRepository.save(channel);
        credentialLifecycleService.connectShopify(channel, accessToken);

        MappingRestoreResult restored = restoreMappings(channel, restoring);
        connectionLogService.logSuccess(channel, action,
                "Connected Shopify channel " + handle, connectionMetadata(restored, Map.of("shopDomain", handle)));
        return responseService.toResponse(channel);
    }

    @Override
    public void registerShopifyWebhooks(String shop, String accessToken, UUID channelId) {
        try {
            safeUpdateShopifyWebhookMetadata(channelId,
                    shopifyWebhookSubscriptionService.registerWebhooks(
                            shopDomainNormalizer.normalizeHandle(shop), accessToken));
        } catch (Exception error) {
            log.warn("[ChannelConnection] Shopify webhook registration failed: {}", error.getMessage());
            safeUpdateShopifyWebhookMetadata(channelId, WebhookRegistrationResult.builder()
                    .status("FAILED").error(error.getMessage()).webhooks(Collections.emptyList()).build());
        }
    }

    @Override
    @Transactional
    public ChannelResponse connectLazada(String accessToken, String refreshToken, int expiresIn,
                                         int refreshExpiresIn, String accountId, String accountName) {
        String resolvedName = firstNonBlank(accountName, accountId, "Connected");
        String resolvedId = firstNonBlank(accountId, resolvedName);
        String displayName = "Lazada-" + resolvedName;
        Channel channel = reconnectResolver.resolveLazada(resolvedId, displayName);
        boolean restoring = isSoftDeleted(channel);
        ChannelConnectionAction action = connectionAction(channel);

        Map<String, Object> metadata = mutableMetadata(channel);
        metadata.put("accountId", resolvedId);
        metadata.put("accountName", resolvedName);
        applyLazadaWebhookMetadata(metadata);
        activate(channel, displayName, metadata);
        channelRepository.save(channel);
        credentialLifecycleService.connectRefreshable(
                channel, accessToken, refreshToken, expiresIn, refreshExpiresIn);

        MappingRestoreResult restored = restoreMappings(channel, restoring);
        Map<String, Object> details = new HashMap<>();
        details.put("accountId", resolvedId);
        details.put("accountName", resolvedName);
        details.put("webhookCallbackUrl", configuredLazadaWebhookCallbackUrl());
        details.put("webhookRegistrationStatus", "MANUAL_CONFIGURATION_REQUIRED");
        connectionLogService.logSuccess(channel, action,
                "Connected Lazada channel " + displayName, connectionMetadata(restored, details));
        return responseService.toResponse(channel);
    }

    @Override
    @Transactional
    public ChannelResponse connectTikTok(String accessToken, String refreshToken, int expiresIn,
                                         int refreshExpiresIn, String accountId, String accountName,
                                         Map<String, Object> metadata) {
        String resolvedName = firstNonBlank(accountName, accountId, "Connected");
        String resolvedId = firstNonBlank(accountId, resolvedName);
        String displayName = "TikTok-" + resolvedName;
        Map<String, Object> incoming = metadata == null ? new HashMap<>() : new HashMap<>(metadata);
        String shopId = text(incoming.get("shopId"));
        String openId = firstNonBlank(text(incoming.get("openId")), text(incoming.get("open_id")));

        Channel channel = reconnectResolver.resolveTikTok(shopId, openId, resolvedId, displayName);
        boolean restoring = isSoftDeleted(channel);
        ChannelConnectionAction action = connectionAction(channel);
        Map<String, Object> persisted = mutableMetadata(channel);
        persisted.putAll(incoming);
        persisted.put("accountId", resolvedId);
        persisted.put("accountName", resolvedName);
        activate(channel, displayName, persisted);
        channelRepository.save(channel);
        credentialLifecycleService.connectRefreshable(
                channel, accessToken, refreshToken, expiresIn, refreshExpiresIn);

        MappingRestoreResult restored = restoreMappings(channel, restoring);
        connectionLogService.logSuccess(channel, action,
                "Connected TikTok channel " + displayName,
                connectionMetadata(restored, Map.of("accountId", resolvedId, "accountName", resolvedName)));
        return responseService.toResponse(channel);
    }

    @Override
    @Transactional
    public void updateShopifyWebhookMetadata(UUID channelId, WebhookRegistrationResult result) {
        Channel channel = activeChannel(channelId);
        Map<String, Object> metadata = mutableMetadata(channel);
        metadata.put("shopifyWebhooks", result.getWebhooks());
        metadata.put("webhookRegistrationStatus", result.getStatus());
        metadata.put("webhookRegistrationError", result.getError());
        channel.setMetadata(metadata);
        channelRepository.save(channel);
    }

    @Override
    @Transactional
    public void disconnect(UUID channelId) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new AppException(ErrorCode.CHANNEL_NOT_FOUND));
        if (channel.getDeletedAt() != null || "DISCONNECTED".equals(channel.getStatus())) {
            throw new AppException(ErrorCode.CHANNEL_ALREADY_DISCONNECTED);
        }

        int archivedCount = mappingLifecycleService.archiveForDisconnect(channelId);
        channel.setStatus("DISCONNECTED");
        channel.setSyncEnabled(false);
        channel.setDeletedAt(OffsetDateTime.now());
        channelRepository.save(channel);
        credentialLifecycleService.disconnect(channelId);
        connectionLogService.logSuccess(channel, ChannelConnectionAction.DISCONNECT,
                "Disconnected channel " + channel.getDisplayName(),
                Map.of("channelName", channel.getDisplayName(), "archivedMappingCount", archivedCount));
        eventPublisher.publishEvent(new ChannelDisconnectedEvent(channelId));
    }

    private MappingRestoreResult restoreMappings(Channel channel, boolean restoring) {
        return restoring ? mappingLifecycleService.restoreAfterReconnect(channel.getId()) : MappingRestoreResult.none();
    }

    private void activate(Channel channel, String displayName, Map<String, Object> metadata) {
        channel.setDisplayName(displayName);
        channel.setStatus("CONNECTED");
        channel.setDeletedAt(null);
        channel.setSyncEnabled(true);
        channel.setMetadata(metadata);
    }

    private Map<String, Object> connectionMetadata(MappingRestoreResult restored, Map<String, Object> base) {
        Map<String, Object> metadata = new HashMap<>(base);
        metadata.put("restoredMappingCount", restored.restoredCount());
        metadata.put("legacyMappingRestore", restored.legacyRestore());
        return metadata;
    }

    private Channel activeChannel(UUID channelId) {
        return channelRepository.findById(channelId)
                .filter(channel -> channel.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.CHANNEL_NOT_FOUND));
    }

    private void safeUpdateShopifyWebhookMetadata(UUID channelId, WebhookRegistrationResult result) {
        try {
            updateShopifyWebhookMetadata(channelId, result);
        } catch (Exception error) {
            log.warn("[ChannelConnection] Failed to persist Shopify webhook metadata: {}", error.getMessage());
        }
    }

    private void applyLazadaWebhookMetadata(Map<String, Object> metadata) {
        String callbackUrl = configuredLazadaWebhookCallbackUrl();
        metadata.put("webhookCallbackUrl", callbackUrl);
        metadata.put("webhookRegistrationStatus", callbackUrl.isBlank()
                ? "MISSING_CALLBACK_URL" : "MANUAL_CONFIGURATION_REQUIRED");
        metadata.put("webhookRegistrationNote",
                "Configure this URL in Lazada Open Platform Push Mechanism and subscribe product/stock messages.");
    }

    private String configuredLazadaWebhookCallbackUrl() {
        return lazadaWebhookCallbackUrl == null ? "" : lazadaWebhookCallbackUrl.trim();
    }

    private Map<String, Object> mutableMetadata(Channel channel) {
        return channel.getMetadata() == null ? new HashMap<>() : new HashMap<>(channel.getMetadata());
    }

    private ChannelConnectionAction connectionAction(Channel channel) {
        return channel.getId() == null ? ChannelConnectionAction.CONNECT : ChannelConnectionAction.RECONNECT;
    }

    private boolean isSoftDeleted(Channel channel) {
        return channel.getId() != null && channel.getDeletedAt() != null;
    }

    private String text(Object value) {
        return value == null ? null : value.toString();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank() && !"null".equalsIgnoreCase(value)) {
                return value;
            }
        }
        return null;
    }
}
