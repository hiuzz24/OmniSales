package fu.osms.channel.service.impl;

import fu.osms.channel.dto.response.ChannelResponse;
import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelCredential;
import fu.osms.channel.entity.ChannelProduct;
import fu.osms.channel.enums.ChannelConnectionAction;
import fu.osms.channel.repository.ChannelCredentialRepository;
import fu.osms.channel.repository.ChannelProductRepository;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.channel.service.ChannelConnectionLogService;
import fu.osms.channel.service.ChannelConnectionService;
import fu.osms.channel.service.ChannelResponseService;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.sync.dto.shopify.WebhookRegistrationResult;
import fu.osms.sync.shopify.ShopifyWebhookSubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelConnectionServiceImpl implements ChannelConnectionService {

    private final ChannelRepository channelRepository;
    private final ChannelCredentialRepository credentialRepository;
    private final ChannelProductRepository channelProductRepository;
    private final ChannelConnectionLogService connectionLogService;
    private final ChannelResponseService responseService;
    private final ShopifyWebhookSubscriptionService shopifyWebhookSubscriptionService;

    @Value("${lazada.webhook-callback-url:}")
    private String lazadaWebhookCallbackUrl;

    @Override
    @Transactional
    public ChannelResponse connectShopify(String shop, String accessToken) {
        String normalizedShop = shop.endsWith(".myshopify.com")
                ? shop.substring(0, shop.length() - ".myshopify.com".length())
                : shop;
        Channel channel = channelRepository.findActiveShopifyByShopDomain(normalizedShop)
                .or(() -> channelRepository.findByPlatformAndDisplayName(PlatformType.SHOPIFY, normalizedShop))
                .orElseGet(() -> Channel.builder()
                        .platform(PlatformType.SHOPIFY)
                        .displayName(normalizedShop)
                        .build());
        ChannelConnectionAction action = connectionAction(channel);

        Map<String, Object> metadata = mutableMetadata(channel);
        metadata.put("shopDomain", normalizedShop);
        activate(channel, metadata);
        channelRepository.save(channel);

        ChannelCredential credential = credential(channel);
        credential.setAccessToken(accessToken);
        credential.setConnectionState("CONNECTED");
        credentialRepository.save(credential);
        connectionLogService.logSuccess(channel, action,
                "Connected Shopify channel " + normalizedShop,
                Map.of("shopDomain", normalizedShop));
        return responseService.toResponse(channel);
    }

    @Override
    public void registerShopifyWebhooks(String shop, String accessToken, UUID channelId) {
        try {
            safeUpdateShopifyWebhookMetadata(channelId,
                    shopifyWebhookSubscriptionService.registerWebhooks(shop, accessToken));
        } catch (Exception error) {
            log.warn("[ChannelConnection] Shopify webhook registration failed: {}", error.getMessage());
            safeUpdateShopifyWebhookMetadata(channelId, WebhookRegistrationResult.builder()
                    .status("FAILED")
                    .error(error.getMessage())
                    .webhooks(Collections.emptyList())
                    .build());
        }
    }

    @Override
    @Transactional
    public ChannelResponse connectLazada(String accessToken, String refreshToken, int expiresIn,
                                         int refreshExpiresIn, String accountId, String accountName) {
        String resolvedName = firstNonBlank(accountName, accountId, "Connected");
        String resolvedId = firstNonBlank(accountId, resolvedName);
        String displayName = "Lazada-" + resolvedName;

        Channel channel = channelRepository.findByPlatformAndDeletedAtIsNull(PlatformType.LAZADA).stream()
                .filter(value -> value.getMetadata() != null
                        && Objects.equals(resolvedId, value.getMetadata().get("accountId")))
                .findFirst()
                .or(() -> channelRepository.findByPlatformAndDeletedAtIsNull(PlatformType.LAZADA).stream()
                        .filter(value -> value.getDisplayName() == null
                                || "Lazada-null".equalsIgnoreCase(value.getDisplayName())
                                || value.getMetadata() == null
                                || value.getMetadata().get("accountId") == null)
                        .findFirst())
                .orElseGet(() -> Channel.builder()
                        .platform(PlatformType.LAZADA)
                        .displayName(displayName)
                        .build());
        ChannelConnectionAction action = connectionAction(channel);

        Map<String, Object> metadata = mutableMetadata(channel);
        metadata.put("accountId", resolvedId);
        metadata.put("accountName", resolvedName);
        applyLazadaWebhookMetadata(metadata);
        activate(channel, metadata);
        if (channel.getDisplayName() == null || channel.getDisplayName().startsWith("Lazada-")) {
            channel.setDisplayName(displayName);
        }
        channelRepository.save(channel);

        saveRefreshableCredential(channel, accessToken, refreshToken, expiresIn, refreshExpiresIn);
        Map<String, Object> logMetadata = new HashMap<>();
        logMetadata.put("accountId", accountId == null ? "" : accountId);
        logMetadata.put("accountName", accountName == null ? "" : accountName);
        logMetadata.put("webhookCallbackUrl", configuredLazadaWebhookCallbackUrl());
        logMetadata.put("webhookRegistrationStatus", "MANUAL_CONFIGURATION_REQUIRED");
        connectionLogService.logSuccess(channel, action,
                "Connected Lazada channel " + displayName, logMetadata);
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

        Map<String, Object> incomingMetadata = metadata == null
                ? new HashMap<>() : new HashMap<>(metadata);
        incomingMetadata.put("accountId", resolvedId);
        incomingMetadata.put("accountName", resolvedName);

        Channel channel = channelRepository.findByPlatformAndDeletedAtIsNull(PlatformType.TIKTOK).stream()
                .filter(value -> value.getMetadata() != null
                        && (Objects.equals(resolvedId, value.getMetadata().get("accountId"))
                        || Objects.equals(resolvedId, value.getMetadata().get("openId"))))
                .findFirst()
                .orElseGet(() -> Channel.builder()
                        .platform(PlatformType.TIKTOK)
                        .displayName(displayName)
                        .build());
        ChannelConnectionAction action = connectionAction(channel);

        Map<String, Object> persistedMetadata = mutableMetadata(channel);
        persistedMetadata.putAll(incomingMetadata);
        activate(channel, persistedMetadata);
        if (channel.getDisplayName() == null || channel.getDisplayName().startsWith("TikTok-")) {
            channel.setDisplayName(displayName);
        }
        channelRepository.save(channel);

        saveRefreshableCredential(channel, accessToken, refreshToken, expiresIn, refreshExpiresIn);
        connectionLogService.logSuccess(channel, action,
                "Connected TikTok channel " + displayName,
                Map.of("accountId", resolvedId, "accountName", resolvedName));
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
        Channel channel = activeChannel(channelId);
        unregisterShopifyWebhooks(channel);
        channel.setDeletedAt(OffsetDateTime.now());
        channelRepository.save(channel);

        credentialRepository.findByChannelId(channelId).ifPresent(value -> {
            value.setConnectionState("DISCONNECTED");
            credentialRepository.save(value);
        });
        List<ChannelProduct> mappings = channelProductRepository.findByChannelId(channelId);
        mappings.forEach(mapping -> mapping.setMappingState("ARCHIVED"));
        channelProductRepository.saveAll(mappings);
        connectionLogService.logSuccess(channel, ChannelConnectionAction.DISCONNECT,
                "Disconnected channel " + channel.getDisplayName(),
                Map.of("channelName", channel.getDisplayName()));
    }

    private void saveRefreshableCredential(Channel channel, String accessToken, String refreshToken,
                                           int expiresIn, int refreshExpiresIn) {
        ChannelCredential credential = credential(channel);
        credential.setAccessToken(accessToken);
        credential.setRefreshToken(refreshToken);
        credential.setConnectionState("CONNECTED");
        OffsetDateTime now = OffsetDateTime.now();
        credential.setTokenExpiresAt(expiresIn > 0 ? now.plusSeconds(expiresIn) : null);
        credential.setRefreshTokenExpiresAt(refreshExpiresIn > 0 ? now.plusSeconds(refreshExpiresIn) : null);
        credential.setLastRefreshedAt(now);
        credential.setRefreshError(null);
        credentialRepository.save(credential);
    }

    private ChannelCredential credential(Channel channel) {
        return credentialRepository.findByChannelId(channel.getId())
                .orElseGet(() -> ChannelCredential.builder().channel(channel).build());
    }

    private Channel activeChannel(UUID channelId) {
        return channelRepository.findById(channelId)
                .filter(value -> value.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.CHANNEL_NOT_FOUND));
    }

    private void activate(Channel channel, Map<String, Object> metadata) {
        boolean restoring = channel.getDeletedAt() != null;
        channel.setStatus("CONNECTED");
        channel.setDeletedAt(null);
        if (restoring || channel.getSyncEnabled() == null) {
            channel.setSyncEnabled(true);
        }
        channel.setMetadata(metadata);
    }

    private ChannelConnectionAction connectionAction(Channel channel) {
        return channel.getId() == null
                ? ChannelConnectionAction.CONNECT
                : ChannelConnectionAction.RECONNECT;
    }

    private Map<String, Object> mutableMetadata(Channel channel) {
        return channel.getMetadata() == null
                ? new HashMap<>() : new HashMap<>(channel.getMetadata());
    }

    private void safeUpdateShopifyWebhookMetadata(UUID channelId, WebhookRegistrationResult result) {
        try {
            updateShopifyWebhookMetadata(channelId, result);
        } catch (Exception error) {
            log.warn("[ChannelConnection] Failed to persist Shopify webhook metadata: {}", error.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void unregisterShopifyWebhooks(Channel channel) {
        if (channel.getPlatform() != PlatformType.SHOPIFY) {
            return;
        }
        try {
            ChannelCredential credential = credentialRepository.findByChannelId(channel.getId()).orElse(null);
            if (credential == null || credential.getAccessToken() == null || credential.getAccessToken().isBlank()) {
                return;
            }
            Map<String, Object> metadata = channel.getMetadata();
            String shopDomain = metadata != null && metadata.get("shopDomain") != null
                    ? metadata.get("shopDomain").toString() : channel.getDisplayName();
            List<Map<String, Object>> webhooks = metadata != null
                    ? (List<Map<String, Object>>) metadata.get("shopifyWebhooks")
                    : Collections.emptyList();
            shopifyWebhookSubscriptionService.unregisterWebhooks(
                    shopDomain, credential.getAccessToken(), webhooks);
        } catch (Exception error) {
            log.warn("[ChannelConnection] Failed to unregister Shopify webhooks: {}", error.getMessage());
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

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank() && !"null".equalsIgnoreCase(value)) {
                return value;
            }
        }
        return null;
    }
}
