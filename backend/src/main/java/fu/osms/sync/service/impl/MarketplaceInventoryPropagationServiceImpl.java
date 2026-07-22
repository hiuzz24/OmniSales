package fu.osms.sync.service.impl;

import fu.osms.channel.entity.Channel;
import fu.osms.channel.repository.ChannelCredentialRepository;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.common.enums.PlatformType;
import fu.osms.sync.lazada.service.LazadaInventoryUpdateService;
import fu.osms.sync.service.MarketplaceInventoryPropagationService;
import fu.osms.sync.service.MarketplaceStockQuantityResolver;
import fu.osms.sync.service.MarketplaceWarehouseConsistencyService;
import fu.osms.sync.shopify.ShopifyInventoryUpdateService;
import fu.osms.sync.tiktok.TikTokInventoryUpdateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketplaceInventoryPropagationServiceImpl implements MarketplaceInventoryPropagationService {

    private final ChannelRepository channelRepository;
    private final ChannelCredentialRepository credentialRepository;
    private final ChannelProductVariantRepository channelProductVariantRepository;
    private final MarketplaceWarehouseConsistencyService marketplaceWarehouseConsistencyService;
    private final ShopifyInventoryUpdateService shopifyInventoryUpdateService;
    private final LazadaInventoryUpdateService lazadaInventoryUpdateService;
    private final TikTokInventoryUpdateService tikTokInventoryUpdateService;
    private final MarketplaceStockQuantityResolver marketplaceStockQuantityResolver;
    private final TransactionTemplate transactionTemplate;

    @Override
    public void schedulePushAvailableStock(Collection<UUID> variantIds) {
        schedulePushAvailableStock(variantIds, null);
    }

    @Override
    public void schedulePushAvailableStock(Collection<UUID> variantIds, UUID excludedChannelId) {
        Set<UUID> scopedVariantIds = marketplaceStockQuantityResolver.expandVariantIdsBySkuGroup(
                sanitizeVariantIds(variantIds)
        );
        if (scopedVariantIds.isEmpty()) {
            return;
        }

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            pushAvailableStock(scopedVariantIds, excludedChannelId);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    transactionTemplate.executeWithoutResult(
                            status -> pushAvailableStock(scopedVariantIds, excludedChannelId));
                } catch (Exception e) {
                    log.error("[MarketplaceInventoryPropagation] Failed to push stock after commit variantIds={}",
                            scopedVariantIds, e);
                }
            }
        });
    }

    @Override
    public void pushAvailableStock(Collection<UUID> variantIds) {
        pushAvailableStock(variantIds, null);
    }

    @Override
    public void pushAvailableStock(Collection<UUID> variantIds, UUID excludedChannelId) {
        Set<UUID> scopedVariantIds = sanitizeVariantIds(variantIds);
        if (scopedVariantIds.isEmpty()) {
            return;
        }

        marketplaceWarehouseConsistencyService.validateConnectedPrimaryWarehouses();
        OffsetDateTime syncStartedAt = OffsetDateTime.now();
        for (Channel channel : connectedMarketplaceChannels()) {
            if (excludedChannelId != null && excludedChannelId.equals(channel.getId())) {
                log.debug("[MarketplaceInventoryPropagation] Skip source channel channelId={}", excludedChannelId);
                continue;
            }
            if (!hasActiveMappings(channel.getId(), scopedVariantIds)) {
                log.debug(
                        "[MarketplaceInventoryPropagation] Skip unrelated channel channelId={} platform={} variantIds={}",
                        channel.getId(),
                        channel.getPlatform(),
                        scopedVariantIds
                );
                continue;
            }
            try {
                if (channel.getPlatform() == PlatformType.SHOPIFY) {
                    shopifyInventoryUpdateService.syncChangedAvailableStock(
                            channel.getId(),
                            null,
                            syncStartedAt,
                            scopedVariantIds
                    );
                } else if (channel.getPlatform() == PlatformType.LAZADA) {
                    lazadaInventoryUpdateService.syncChangedSellableStock(
                            channel.getId(),
                            null,
                            syncStartedAt,
                            scopedVariantIds
                    );
                } else if (channel.getPlatform() == PlatformType.TIKTOK) {
                    tikTokInventoryUpdateService.pushAvailableStock(channel.getId(), scopedVariantIds);
                }
            } catch (Exception e) {
                log.error("[MarketplaceInventoryPropagation] Failed to push stock channelId={} platform={} variantIds={}",
                        channel.getId(), channel.getPlatform(), scopedVariantIds, e);
            }
        }
    }

    private List<Channel> connectedMarketplaceChannels() {
        List<Channel> result = new ArrayList<>();
        for (Channel channel : channelRepository.findByDeletedAtIsNull()) {
            if (!Boolean.TRUE.equals(channel.getSyncEnabled()) || !isSupported(channel.getPlatform())) {
                continue;
            }
            if (credentialRepository.findByChannelIdAndConnectionState(channel.getId(), "CONNECTED")
                    .filter(credential -> credential.getAccessToken() != null && !credential.getAccessToken().isBlank())
                    .isPresent()) {
                result.add(channel);
            }
        }
        return result;
    }

    private boolean hasActiveMappings(UUID channelId, Collection<UUID> variantIds) {
        if (channelId == null || variantIds == null || variantIds.isEmpty()) {
            return false;
        }
        return !channelProductVariantRepository
                .findActiveByChannelIdAndVariantIdInWithVariant(channelId, new ArrayList<>(variantIds))
                .isEmpty();
    }

    private boolean isSupported(PlatformType platform) {
        return platform == PlatformType.SHOPIFY || platform == PlatformType.LAZADA || platform == PlatformType.TIKTOK;
    }

    private Set<UUID> sanitizeVariantIds(Collection<UUID> variantIds) {
        if (variantIds == null) {
            return Set.of();
        }
        Set<UUID> result = new LinkedHashSet<>();
        for (UUID variantId : variantIds) {
            if (variantId != null) {
                result.add(variantId);
            }
        }
        return result;
    }
}
