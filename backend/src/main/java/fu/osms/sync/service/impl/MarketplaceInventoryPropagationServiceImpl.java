package fu.osms.sync.service.impl;

import fu.osms.channel.entity.Channel;
import fu.osms.channel.repository.ChannelCredentialRepository;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.enums.SyncStatus;
import fu.osms.messaging.constants.RabbitMQConstants;
import fu.osms.messaging.dto.InventoryPushMessage;
import fu.osms.messaging.publisher.EventPublisher;
import fu.osms.sync.lazada.service.LazadaInventoryUpdateService;
import fu.osms.sync.lazada.dto.LazadaInventorySyncResult;
import fu.osms.sync.service.InventoryAutoPushSyncLogService;
import fu.osms.sync.service.MarketplaceInventoryPropagationService;
import fu.osms.sync.service.MarketplaceStockQuantityResolver;
import fu.osms.sync.service.MarketplaceWarehouseConsistencyService;
import fu.osms.sync.shopify.ShopifyInventoryUpdateService;
import fu.osms.sync.tiktok.TikTokInventoryUpdateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;

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
    private final InventoryAutoPushSyncLogService inventoryAutoPushSyncLogService;
    private final EventPublisher eventPublisher;
    @Qualifier("syncJobExecutor")
    private final Executor syncJobExecutor;

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
                eventPublisher.publish(
                        RabbitMQConstants.INVENTORY_UPDATED,
                        new InventoryPushMessage(scopedVariantIds, excludedChannelId),
                        () -> scheduleAsyncPush(scopedVariantIds, excludedChannelId));
            }
        });
    }

    private void scheduleAsyncPush(Set<UUID> variantIds, UUID excludedChannelId) {
        try {
            syncJobExecutor.execute(() -> {
                try {
                    pushAvailableStock(variantIds, excludedChannelId);
                } catch (Exception e) {
                    log.error("[MarketplaceInventoryPropagation] Async push failed after commit variantIds={}",
                            variantIds, e);
                }
            });
        } catch (TaskRejectedException e) {
            log.warn("[MarketplaceInventoryPropagation] Async queue full, pushing inline variantIds={}", variantIds);
            try {
                pushAvailableStock(variantIds, excludedChannelId);
            } catch (Exception inlineException) {
                log.error("[MarketplaceInventoryPropagation] Inline fallback push failed variantIds={}",
                        variantIds, inlineException);
            }
        }
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
        List<String> failures = new ArrayList<>();
        for (Channel channel : connectedMarketplaceChannels()) {
            if (excludedChannelId != null && excludedChannelId.equals(channel.getId())) {
                log.debug("[MarketplaceInventoryPropagation] Skip source channel channelId={}", excludedChannelId);
                continue;
            }
            int mappedVariantCount = activeMappingCount(channel.getId(), scopedVariantIds);
            if (mappedVariantCount == 0) {
                log.debug(
                        "[MarketplaceInventoryPropagation] Skip unrelated channel channelId={} platform={} variantIds={}",
                        channel.getId(),
                        channel.getPlatform(),
                        scopedVariantIds
                );
                continue;
            }
            UUID syncLogId = inventoryAutoPushSyncLogService.start(channel.getId(), mappedVariantCount);
            try {
                int pushedVariantCount = 0;
                if (channel.getPlatform() == PlatformType.SHOPIFY) {
                    pushedVariantCount = shopifyInventoryUpdateService.syncChangedAvailableStock(
                            channel.getId(),
                            null,
                            syncStartedAt,
                            scopedVariantIds
                    );
                } else if (channel.getPlatform() == PlatformType.LAZADA) {
                    LazadaInventorySyncResult result = lazadaInventoryUpdateService.syncChangedSellableStock(
                            channel.getId(),
                            null,
                            syncStartedAt,
                            scopedVariantIds
                    );
                    pushedVariantCount = result.pushedVariantCount();
                } else if (channel.getPlatform() == PlatformType.TIKTOK) {
                    pushedVariantCount = tikTokInventoryUpdateService.pushAvailableStock(
                            channel.getId(),
                            scopedVariantIds
                    );
                }
                inventoryAutoPushSyncLogService.markSynced(syncLogId, pushedVariantCount);
            } catch (Exception e) {
                String errorMessage = rootMessage(e);
                log.error("[MarketplaceInventoryPropagation] Failed to push stock channelId={} platform={} variantIds={}",
                        channel.getId(), channel.getPlatform(), scopedVariantIds, e);
                inventoryAutoPushSyncLogService.markFailed(syncLogId, mappedVariantCount, errorMessage);
                markMappingsFailed(channel.getId(), scopedVariantIds);
                failures.add(channel.getDisplayName() + " (" + channel.getPlatform() + "): " + errorMessage);
            }
        }
        if (!failures.isEmpty()) {
            throw new IllegalStateException("Đồng bộ tồn kho thất bại: " + String.join("; ", failures));
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

    private int activeMappingCount(UUID channelId, Collection<UUID> variantIds) {
        if (channelId == null || variantIds == null || variantIds.isEmpty()) {
            return 0;
        }
        return (int) channelProductVariantRepository
                .findActiveByChannelIdAndVariantIdInWithVariant(channelId, new ArrayList<>(variantIds))
                .stream()
                .map(mapping -> mapping.getVariant().getId())
                .distinct()
                .count();
    }

    private boolean isSupported(PlatformType platform) {
        return platform == PlatformType.SHOPIFY || platform == PlatformType.LAZADA || platform == PlatformType.TIKTOK;
    }

    private void markMappingsFailed(UUID channelId, Collection<UUID> variantIds) {
        List<fu.osms.channel.entity.ChannelProductVariant> mappings = channelProductVariantRepository
                .findActiveByChannelIdAndVariantIdInWithVariant(channelId, new ArrayList<>(variantIds));
        mappings.forEach(mapping -> mapping.setSyncStatus(SyncStatus.FAILED));
        channelProductVariantRepository.saveAll(mappings);
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
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
