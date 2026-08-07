package fu.osms.sync.shopify.impl;

import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelCredential;
import fu.osms.channel.enums.ChannelConnectionAction;
import fu.osms.channel.repository.ChannelCredentialRepository;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.channel.service.ChannelConnectionLogService;
import fu.osms.common.enums.PlatformType;
import fu.osms.sync.shopify.ShopifyDisconnectCleanupService;
import fu.osms.sync.shopify.ShopifyShopDomainNormalizer;
import fu.osms.sync.shopify.ShopifyWebhookSubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShopifyDisconnectCleanupServiceImpl implements ShopifyDisconnectCleanupService {

    private final ChannelRepository channelRepository;
    private final ChannelCredentialRepository credentialRepository;
    private final ShopifyShopDomainNormalizer shopDomainNormalizer;
    private final ShopifyWebhookSubscriptionService webhookSubscriptionService;
    private final ChannelConnectionLogService connectionLogService;
    private final TransactionTemplate transactionTemplate;

    @Override
    public void cleanup(UUID channelId) {
        CleanupSnapshot snapshot = transactionTemplate.execute(status -> snapshot(channelId));
        if (snapshot == null) {
            return;
        }
        try {
            webhookSubscriptionService.unregisterWebhooks(
                    snapshot.shopHandle(), snapshot.accessToken(), snapshot.webhooks());
        } catch (Exception error) {
            log.warn("[ShopifyDisconnect] Webhook cleanup failed channelId={}: {}",
                    channelId, error.getMessage());
            connectionLogService.logFailure(
                    PlatformType.SHOPIFY,
                    ChannelConnectionAction.DISCONNECT,
                    "Disconnected channel but failed to unregister Shopify webhooks",
                    error.getMessage(),
                    Map.of("channelId", channelId.toString(), "channelName", snapshot.channelName()));
        }
    }

    @SuppressWarnings("unchecked")
    private CleanupSnapshot snapshot(UUID channelId) {
        Channel channel = channelRepository.findById(channelId).orElse(null);
        if (channel == null || channel.getPlatform() != PlatformType.SHOPIFY) {
            return null;
        }
        ChannelCredential credential = credentialRepository.findByChannelId(channelId).orElse(null);
        if (credential == null || credential.getAccessToken() == null || credential.getAccessToken().isBlank()) {
            return null;
        }
        Map<String, Object> metadata = channel.getMetadata();
        Object storedShop = metadata == null ? null : metadata.get("shopDomain");
        String shopHandle = shopDomainNormalizer.normalizeHandle(
                storedShop == null ? channel.getDisplayName() : storedShop.toString());
        List<Map<String, Object>> webhooks = metadata != null && metadata.get("shopifyWebhooks") instanceof List<?> list
                ? (List<Map<String, Object>>) list
                : Collections.emptyList();
        return new CleanupSnapshot(channel.getDisplayName(), shopHandle,
                credential.getAccessToken(), List.copyOf(webhooks));
    }

    private record CleanupSnapshot(String channelName, String shopHandle, String accessToken,
                                   List<Map<String, Object>> webhooks) {
    }
}
