package fu.osms.channel.service.impl;

import fu.osms.channel.dto.request.ChannelRequest;
import fu.osms.channel.dto.request.CreateManualChannelRequest;
import fu.osms.channel.dto.response.ChannelCredentialResponse;
import fu.osms.channel.dto.response.ChannelProductResponse;
import fu.osms.channel.dto.response.ChannelResponse;
import fu.osms.channel.dto.response.ChannelSyncResponse;
import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelCredential;
import fu.osms.channel.entity.ChannelProduct;
import fu.osms.channel.enums.ChannelConnectionAction;
import fu.osms.channel.mapper.ChannelCredentialMapper;
import fu.osms.channel.mapper.ChannelMapper;
import fu.osms.channel.mapper.ChannelProductMapper;
import fu.osms.channel.repository.ChannelCredentialRepository;
import fu.osms.channel.repository.ChannelProductRepository;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.channel.service.ChannelConnectionLogService;
import fu.osms.channel.service.ChannelService;
import fu.osms.common.dto.PageResponse;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.common.enums.PlatformType;
import fu.osms.sync.dto.shopify.WebhookRegistrationResult;
import fu.osms.sync.shopify.ShopifyWebhookSubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelServiceImpl implements ChannelService {

    private final ChannelRepository channelRepository;
    private final ChannelCredentialRepository credentialRepository;
    private final ChannelProductRepository channelProductRepository;
    private final ChannelProductVariantRepository channelProductVariantRepository;
    private final ChannelMapper channelMapper;
    private final ChannelProductMapper channelProductMapper;
    private final ChannelConnectionLogService channelConnectionLogService;
    private final ShopifyWebhookSubscriptionService shopifyWebhookSubscriptionService;

    @Override
    @Transactional
    public ChannelResponse create(ChannelRequest request) {
        if (channelRepository.existsByPlatformAndDisplayName(request.getPlatform(), request.getDisplayName())) {
            throw new AppException(ErrorCode.CHANNEL_ALREADY_EXISTS);
        }

        Channel channel = channelMapper.toEntity(request);
        channel.setStatus("CONNECTED");
        channelRepository.save(channel);

        ChannelCredential credential = ChannelCredential.builder()
                .channel(channel)
                .accessToken(null)
                .refreshToken(null)
                .connectionState("CONNECTED")
                .build();
        credentialRepository.save(credential);

        return channelMapper.toResponse(channel);
    }

    @Override
    @Transactional(readOnly = true)
    public ChannelResponse getById(UUID id) {
        Channel channel = channelRepository.findById(id)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.CHANNEL_NOT_FOUND));
        enrichChannelStats(channel);
        return channelMapper.toResponse(channel);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChannelResponse> getAll() {
        return channelRepository.findByDeletedAtIsNull()
                .stream()
                .peek(this::enrichChannelStats)
                .map(channelMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public ChannelResponse update(UUID id, ChannelRequest request) {
        Channel channel = channelRepository.findById(id)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.CHANNEL_NOT_FOUND));

        channel.setDisplayName(request.getDisplayName());
        channel.setCommissionRate(request.getCommissionRate());
        if (request.getMetadata() != null) {
            channel.setMetadata(request.getMetadata());
        }
        if (request.getSyncEnabled() != null) {
            channel.setSyncEnabled(request.getSyncEnabled());
        }
        channelRepository.save(channel);



        return channelMapper.toResponse(channel);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Channel channel = channelRepository.findById(id)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.CHANNEL_NOT_FOUND));

        unregisterShopifyWebhooks(channel);

        channel.setDeletedAt(OffsetDateTime.now());
        channelRepository.save(channel);

        credentialRepository.findByChannelId(id).ifPresent(cred -> {
            cred.setConnectionState("DISCONNECTED");
            credentialRepository.save(cred);
        });

        List<ChannelProduct> mappedProducts = channelProductRepository.findByChannelId(id);
        for (ChannelProduct cp : mappedProducts) {
            cp.setMappingState("ARCHIVED");
        }
        channelProductRepository.saveAll(mappedProducts);

        channelConnectionLogService.logSuccess(
                channel,
                ChannelConnectionAction.DISCONNECT,
                "Disconnected channel " + channel.getDisplayName(),
                Map.of("channelName", channel.getDisplayName())
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ChannelProductResponse> getChannelProducts(UUID channelId, int page, int size) {
        throw new UnsupportedOperationException("Chưa code");
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, List<String>> getProductChannels(Collection<UUID> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<ChannelProduct> channelProducts = channelProductRepository.findByProductIdInAndMappingState(productIds, "ACTIVE");
        return channelProducts.stream()
                .filter(cp -> cp.getProduct() != null && cp.getChannel() != null)
                .collect(Collectors.groupingBy(
                        cp -> cp.getProduct().getId(),
                        Collectors.mapping(
                                cp -> cp.getChannel().getPlatform().name(),
                                Collectors.collectingAndThen(
                                        Collectors.toList(),
                                        list -> list.stream().distinct().collect(Collectors.toList())
                                )
                        )
                ));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, List<UUID>> getProductChannelIds(Collection<UUID> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<ChannelProduct> channelProducts = channelProductRepository.findByProductIdInAndMappingState(productIds, "ACTIVE");
        return channelProducts.stream()
                .filter(cp -> cp.getProduct() != null && cp.getChannel() != null)
                .collect(Collectors.groupingBy(
                        cp -> cp.getProduct().getId(),
                        Collectors.mapping(
                                cp -> cp.getChannel().getId(),
                                Collectors.collectingAndThen(
                                        Collectors.toList(),
                                        list -> list.stream().distinct().collect(Collectors.toList())
                                )
                        )
                ));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, List<ChannelSyncResponse>> getProductChannelSyncs(Collection<UUID> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<ChannelProduct> channelProducts = channelProductRepository.findByProductIdInAndMappingState(productIds, "ACTIVE");
        return channelProducts.stream()
                .filter(cp -> cp.getProduct() != null && cp.getChannel() != null)
                .collect(Collectors.groupingBy(
                        cp -> cp.getProduct().getId(),
                        Collectors.mapping(
                                cp -> ChannelSyncResponse.builder()
                                        .platform(cp.getChannel().getPlatform().name())
                                        .syncStatus(cp.getSyncStatus())
                                        .lastSyncedAt(cp.getLastSyncedAt())
                                        .lastSyncError(cp.getLastSyncError())
                                        .build(),
                                Collectors.toList()
                        )
                ));
    }

    @Override
    @Transactional
    public ChannelResponse connectShopify(String shop, String accessToken) {
        log.info("1");
        String normalizedShop = shop.endsWith(".myshopify.com")
                ? shop.substring(0, shop.length() - ".myshopify.com".length())
                : shop;

        log.info("[ChannelService] connectShopify — shop={}", normalizedShop);

        Channel channel = channelRepository
                .findActiveShopifyByShopDomain(normalizedShop)
                .or(() -> channelRepository.findByPlatformAndDisplayName(PlatformType.SHOPIFY, normalizedShop))
                .orElseGet(() -> Channel.builder()
                        .platform(PlatformType.SHOPIFY)
                        .displayName(normalizedShop)
                        .build());
        ChannelConnectionAction action = channel.getId() == null
                ? ChannelConnectionAction.CONNECT
                : ChannelConnectionAction.RECONNECT;

        Map<String, Object> metadata = channel.getMetadata() == null
                ? new HashMap<>()
                : new HashMap<>(channel.getMetadata());
        metadata.put("shopDomain", normalizedShop);

        channel.setStatus("CONNECTED");
        channel.setMetadata(metadata);
        channelRepository.save(channel);

        ChannelCredential credential = credentialRepository
                .findByChannelId(channel.getId())
                .orElseGet(() -> ChannelCredential.builder().channel(channel).build());

        credential.setAccessToken(accessToken);
        credential.setConnectionState("CONNECTED");
        credentialRepository.save(credential);
        channelConnectionLogService.logSuccess(
                channel,
                action,
                "Connected Shopify channel " + normalizedShop,
                Map.of("shopDomain", normalizedShop)
        );

        log.info("[ChannelService] connectShopify success — channelId={}", channel.getId());
        return channelMapper.toResponse(channel);
    }

    @Override
    public void registerShopifyWebhooks(String shop, String accessToken, UUID channelId) {
        try {
            log.info("2");
            WebhookRegistrationResult result = shopifyWebhookSubscriptionService.registerWebhooks(shop, accessToken);
            safeUpdateShopifyWebhookMetadata(channelId, result);
            log.info("[ChannelService] Shopify webhook registration status={} shop={}", result.getStatus(), shop);
        } catch (Exception e) {
            log.warn("[ChannelService] Shopify webhook registration failed but channel connected: {}", e.getMessage());
            WebhookRegistrationResult result = WebhookRegistrationResult.builder()
                    .status("FAILED")
                    .error(e.getMessage())
                    .webhooks(Collections.emptyList())
                    .build();
            safeUpdateShopifyWebhookMetadata(channelId, result);
        }
    }

    @Override
    @Transactional
    public ChannelResponse connectLazada(String accessToken, String refreshToken, int expiresIn, String accountId, String accountName) {
        log.info("[ChannelService] connectLazada — accountId={}, accountName={}", accountId, accountName);

        String resolvedAccountName = firstNonBlank(accountName, accountId, "Connected");
        String resolvedAccountId = firstNonBlank(accountId, resolvedAccountName);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("accountId", resolvedAccountId);
        metadata.put("accountName", resolvedAccountName);

        String displayName = "Lazada-" + resolvedAccountName;

        Channel channel = channelRepository
                .findByPlatformAndDeletedAtIsNull(PlatformType.LAZADA).stream()
                .filter(c -> c.getMetadata() != null && java.util.Objects.equals(resolvedAccountId, c.getMetadata().get("accountId")))
                .findFirst()
                .or(() -> channelRepository.findByPlatformAndDeletedAtIsNull(PlatformType.LAZADA).stream()
                        .filter(c -> c.getDisplayName() == null
                                || "Lazada-null".equalsIgnoreCase(c.getDisplayName())
                                || c.getMetadata() == null
                                || c.getMetadata().get("accountId") == null)
                        .findFirst())
                .orElseGet(() -> Channel.builder()
                        .platform(PlatformType.LAZADA)
                        .displayName(displayName)
                        .build());
        ChannelConnectionAction action = channel.getId() == null
                ? ChannelConnectionAction.CONNECT
                : ChannelConnectionAction.RECONNECT;

        channel.setStatus("CONNECTED");
        channel.setMetadata(metadata);
        if (channel.getDisplayName() == null || channel.getDisplayName().startsWith("Lazada-")) {
            channel.setDisplayName(displayName);
        }
        channelRepository.save(channel);

        ChannelCredential credential = credentialRepository
                .findByChannelId(channel.getId())
                .orElseGet(() -> ChannelCredential.builder().channel(channel).build());

        credential.setAccessToken(accessToken);
        credential.setRefreshToken(refreshToken);
        credential.setConnectionState("CONNECTED");
        
        OffsetDateTime tokenExpiresAt = OffsetDateTime.now().plusSeconds(expiresIn);
        credential.setTokenExpiresAt(tokenExpiresAt);

        credentialRepository.save(credential);
        channelConnectionLogService.logSuccess(
                channel,
                action,
                "Connected Lazada channel " + displayName,
                Map.of(
                        "accountId", accountId != null ? accountId : "",
                        "accountName", accountName != null ? accountName : ""
                )
        );

        log.info("[ChannelService] connectLazada success — channelId={}, expiresAt={}", channel.getId(), tokenExpiresAt);
        return channelMapper.toResponse(channel);
    }

    @Override
    @Transactional
    public ChannelResponse connectTikTok(String accessToken, String refreshToken, int expiresIn, String accountId, String accountName, Map<String, Object> metadata) {
        log.info("[ChannelService] connectTikTok - accountId={}, accountName={}", accountId, accountName);

        String resolvedAccountName = firstNonBlank(accountName, accountId, "Connected");
        String resolvedAccountId = firstNonBlank(accountId, resolvedAccountName);
        String displayName = "TikTok-" + resolvedAccountName;

        Map<String, Object> resolvedMetadata = metadata == null
                ? new HashMap<>()
                : new HashMap<>(metadata);
        resolvedMetadata.put("accountId", resolvedAccountId);
        resolvedMetadata.put("accountName", resolvedAccountName);

        Channel channel = channelRepository
                .findByPlatformAndDeletedAtIsNull(PlatformType.TIKTOK).stream()
                .filter(c -> c.getMetadata() != null
                        && (java.util.Objects.equals(resolvedAccountId, c.getMetadata().get("accountId"))
                        || java.util.Objects.equals(resolvedAccountId, c.getMetadata().get("openId"))))
                .findFirst()
                .orElseGet(() -> Channel.builder()
                        .platform(PlatformType.TIKTOK)
                        .displayName(displayName)
                        .build());
        ChannelConnectionAction action = channel.getId() == null
                ? ChannelConnectionAction.CONNECT
                : ChannelConnectionAction.RECONNECT;

        channel.setStatus("CONNECTED");
        channel.setMetadata(resolvedMetadata);
        if (channel.getDisplayName() == null || channel.getDisplayName().startsWith("TikTok-")) {
            channel.setDisplayName(displayName);
        }
        channelRepository.save(channel);

        ChannelCredential credential = credentialRepository
                .findByChannelId(channel.getId())
                .orElseGet(() -> ChannelCredential.builder().channel(channel).build());

        credential.setAccessToken(accessToken);
        credential.setRefreshToken(refreshToken);
        credential.setConnectionState("CONNECTED");
        if (expiresIn > 0) {
            credential.setTokenExpiresAt(OffsetDateTime.now().plusSeconds(expiresIn));
        }
        credentialRepository.save(credential);

        channelConnectionLogService.logSuccess(
                channel,
                action,
                "Connected TikTok channel " + displayName,
                Map.of(
                        "accountId", resolvedAccountId,
                        "accountName", resolvedAccountName
                )
        );

        log.info("[ChannelService] connectTikTok success - channelId={}", channel.getId());
        return channelMapper.toResponse(channel);
    }

    @Override
    @Transactional
    public void updateShopifyWebhookMetadata(UUID channelId, WebhookRegistrationResult result) {
        Channel channel = channelRepository.findById(channelId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.CHANNEL_NOT_FOUND));

        Map<String, Object> metadata = channel.getMetadata() == null
                ? new HashMap<>()
                : new HashMap<>(channel.getMetadata());
        metadata.put("shopifyWebhooks", result.getWebhooks());
        metadata.put("webhookRegistrationStatus", result.getStatus());
        metadata.put("webhookRegistrationError", result.getError());

        channel.setMetadata(metadata);
        channelRepository.save(channel);
    }

    private void safeUpdateShopifyWebhookMetadata(UUID channelId, WebhookRegistrationResult result) {
        try {
            updateShopifyWebhookMetadata(channelId, result);
        } catch (Exception e) {
            log.warn("[ChannelService] Failed to update Shopify webhook metadata for channel {}: {}", channelId, e.getMessage());
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
                    ? metadata.get("shopDomain").toString()
                    : channel.getDisplayName();
            List<Map<String, Object>> savedWebhooks = metadata != null
                    ? (List<Map<String, Object>>) metadata.get("shopifyWebhooks")
                    : Collections.emptyList();

            shopifyWebhookSubscriptionService.unregisterWebhooks(shopDomain, credential.getAccessToken(), savedWebhooks);
        } catch (Exception e) {
            log.warn("[ChannelService] Failed to unregister Shopify webhooks for channel {}: {}", channel.getId(), e.getMessage());
        }
    }

    private void enrichChannelStats(Channel channel) {
        Map<String, Object> metadata = channel.getMetadata() == null
                ? new HashMap<>()
                : new HashMap<>(channel.getMetadata());
        metadata.put("productCount", channelProductRepository.countByChannelIdAndMappingState(channel.getId(), "ACTIVE"));
        metadata.put("skuVariantCount", channelProductVariantRepository.countActiveByChannelId(channel.getId()));
        metadata.putIfAbsent("warehouseCount", 0);
        channel.setMetadata(metadata);
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
