package fu.osms.sync.service.impl;

import fu.osms.channel.dto.response.ChannelImportSyncResponse;
import fu.osms.channel.dto.response.ChannelSyncDetailResponse;
import fu.osms.channel.entity.Channel;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.channel.service.ChannelConnectionValidator;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.enums.SyncStatus;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.inventory.repository.InventoryIssueRepository;
import fu.osms.inventory.repository.StockReceiveRepository;
import fu.osms.sync.entity.SyncLog;
import fu.osms.sync.lazada.dto.LazadaInventorySyncResult;
import fu.osms.sync.lazada.service.LazadaInventoryUpdateService;
import fu.osms.sync.repository.SyncLogRepository;
import fu.osms.sync.service.ChannelLocalSyncService;
import fu.osms.sync.service.MarketplaceWarehouseConsistencyService;
import fu.osms.sync.shopify.ShopifyInventoryUpdateService;
import fu.osms.sync.tiktok.TikTokInventoryUpdateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelLocalSyncServiceImpl implements ChannelLocalSyncService {

    private final ChannelRepository channelRepository;
    private final ChannelConnectionValidator channelConnectionValidator;
    private final SyncLogRepository syncLogRepository;
    private final LazadaInventoryUpdateService lazadaInventoryUpdateService;
    private final ShopifyInventoryUpdateService shopifyInventoryUpdateService;
    private final TikTokInventoryUpdateService tikTokInventoryUpdateService;
    private final StockReceiveRepository stockReceiveRepository;
    private final InventoryIssueRepository inventoryIssueRepository;
    private final MarketplaceWarehouseConsistencyService marketplaceWarehouseConsistencyService;
    private final TransactionTemplate transactionTemplate;

    @Override
    public ChannelImportSyncResponse syncAllLocalChanges(UUID requestedChannelId) {
        channelConnectionValidator.requireConnected(requestedChannelId);
        return syncAllLocalChanges();
    }

    @Override
    public ChannelImportSyncResponse syncAllLocalChanges() {
        marketplaceWarehouseConsistencyService.validateConnectedPrimaryWarehouses();

        int pushedVariantCount = 0;
        int warehouseCount = 0;
        int failedCount = 0;
        List<String> failedMessages = new ArrayList<>();
        List<ChannelSyncDetailResponse> details = new ArrayList<>();

        List<Channel> channels = channelRepository.findByDeletedAtIsNull().stream()
                .filter(channel -> Boolean.TRUE.equals(channel.getSyncEnabled()))
                .filter(channel -> channel.getPlatform() == PlatformType.SHOPIFY
                        || channel.getPlatform() == PlatformType.LAZADA
                        || channel.getPlatform() == PlatformType.TIKTOK)
                .toList();

        for (Channel channel : channels) {
            long startedAt = System.currentTimeMillis();
            try {
                ChannelImportSyncResponse response = transactionTemplate.execute(status ->
                        syncLocalChanges(channel.getId())
                );
                if (response == null) {
                    continue;
                }
                pushedVariantCount += response.getPushedVariantCount();
                warehouseCount += response.getWarehouseCount();
                details.add(detail(channel, response, response.getStatus(), response.getMessage(), startedAt));
            } catch (Exception e) {
                failedCount++;
                failedMessages.add(channel.getDisplayName() + ": " + e.getMessage());
                log.error("[ChannelLocalSync] Sync all failed for channelId={}", channel.getId(), e);
                details.add(detail(channel, null, SyncStatus.FAILED.name(), e.getMessage(), startedAt));
            }
        }

        String message;
        if (failedCount > 0) {
            message = "Đồng bộ hoàn tất một phần. Lỗi " + failedCount + " kênh: " + failedMessages;
        } else if (pushedVariantCount == 0) {
            message = "Không có phiếu nhập hoặc phiếu xuất kho mới cần đồng bộ.";
        } else {
            message = "Đã đồng bộ tồn kho và giá từ các phiếu nhập/xuất kho lên tất cả sàn đã liên kết.";
        }

        return ChannelImportSyncResponse.builder()
                .channelId(null)
                .productCount(0)
                .variantCount(pushedVariantCount)
                .warehouseCount(warehouseCount)
                .pushedVariantCount(pushedVariantCount)
                .status(failedCount == 0 ? SyncStatus.SYNCED.name() : SyncStatus.FAILED.name())
                .message(message)
                .details(details)
                .build();
    }

    @Override
    @Transactional
    public ChannelImportSyncResponse syncLocalChanges(UUID channelId) {
        Channel channel = channelConnectionValidator.requireConnected(channelId);

        if (!Boolean.TRUE.equals(channel.getSyncEnabled())) {
            throw new IllegalStateException("Kênh đang tắt đồng bộ.");
        }

        marketplaceWarehouseConsistencyService.validateConnectedPrimaryWarehouses();
        validateSellerBinding(channel);

        if (channel.getPlatform() != PlatformType.LAZADA
                && channel.getPlatform() != PlatformType.SHOPIFY
                && channel.getPlatform() != PlatformType.TIKTOK) {
            throw new IllegalArgumentException("Chỉ hỗ trợ đồng bộ tồn kho cho Lazada, Shopify và TikTok Shop.");
        }

        return syncInventoryDocuments(channel);
    }

    private ChannelImportSyncResponse syncInventoryDocuments(Channel channel) {
        SyncLog syncLog = syncLogRepository.save(SyncLog.builder()
                .channel(channel)
                .jobType(channel.getPlatform().name() + "_APPLICATION_INVENTORY_SYNC")
                .status(SyncStatus.PENDING)
                .startedAt(OffsetDateTime.now())
                .build());

        try {
            OffsetDateTime syncStartedAt = OffsetDateTime.now();
            OffsetDateTime changedSince = channel.getLastSyncedApplicationAt();
            Set<UUID> changedVariantIds = findStockChangedVariantIds(changedSince, syncStartedAt);

            log.info(
                    "[ChannelLocalSync] Resolved inventory documents channelId={} platform={} changedSince={} changedUntil={} variantCount={}",
                    channel.getId(),
                    channel.getPlatform(),
                    changedSince,
                    syncStartedAt,
                    changedVariantIds.size()
            );

            if (changedVariantIds.isEmpty()) {
                channel.setLastSyncedApplicationAt(syncStartedAt);
                channelRepository.save(channel);
                completeSyncLog(syncLog, 0, syncStartedAt);
                return response(channel, syncLog, 0, 0,
                        "Không có phiếu nhập hoặc phiếu xuất kho mới cần đồng bộ.");
            }

            int pushedVariantCount;
            int changedWarehouseCount = 0;
            if (channel.getPlatform() == PlatformType.LAZADA) {
                LazadaInventorySyncResult result = lazadaInventoryUpdateService.syncChangedSellableStock(
                        channel.getId(), changedSince, syncStartedAt, changedVariantIds);
                pushedVariantCount = result.pushedVariantCount();
                changedWarehouseCount = result.changedWarehouseCount();
            } else if (channel.getPlatform() == PlatformType.SHOPIFY) {
                pushedVariantCount = shopifyInventoryUpdateService.syncChangedAvailableStock(
                        channel.getId(), changedSince, syncStartedAt, changedVariantIds);
            } else {
                pushedVariantCount = tikTokInventoryUpdateService.pushAvailableStock(
                        channel.getId(), changedVariantIds);
            }

            Map<String, Object> metadata = channel.getMetadata() == null
                    ? new HashMap<>()
                    : new HashMap<>(channel.getMetadata());
            metadata.put("lastPushedSkuVariantCount", pushedVariantCount);
            metadata.put("lastChangedWarehouseCount", changedWarehouseCount);
            channel.setMetadata(metadata);
            channel.setLastSyncedApplicationAt(syncStartedAt);
            channelRepository.save(channel);
            completeSyncLog(syncLog, pushedVariantCount, OffsetDateTime.now());

            String message = pushedVariantCount == 0
                    ? "Không có SKU đã liên kết cần cập nhật trên " + channel.getPlatform() + "."
                    : "Đã cập nhật tồn kho và giá từ phiếu nhập/xuất kho lên " + channel.getPlatform() + ".";
            return response(channel, syncLog, pushedVariantCount, changedWarehouseCount, message);
        } catch (Exception e) {
            syncLog.setStatus(SyncStatus.FAILED);
            syncLog.setFailCount(1);
            syncLog.setErrorSummary(e.getMessage());
            syncLog.setCompletedAt(OffsetDateTime.now());
            syncLogRepository.save(syncLog);
            throw e;
        }
    }

    private ChannelImportSyncResponse response(Channel channel,
                                               SyncLog syncLog,
                                               int pushedVariantCount,
                                               int changedWarehouseCount,
                                               String message) {
        return ChannelImportSyncResponse.builder()
                .channelId(channel.getId())
                .syncLogId(syncLog.getId())
                .productCount(0)
                .variantCount(pushedVariantCount)
                .warehouseCount(changedWarehouseCount)
                .pushedVariantCount(pushedVariantCount)
                .status(SyncStatus.SYNCED.name())
                .message(message)
                .build();
    }

    private void completeSyncLog(SyncLog syncLog, int pushedVariantCount, OffsetDateTime completedAt) {
        syncLog.setStatus(SyncStatus.SYNCED);
        syncLog.setTotalItems(pushedVariantCount);
        syncLog.setSuccessCount(pushedVariantCount);
        syncLog.setFailCount(0);
        syncLog.setCompletedAt(completedAt);
        syncLogRepository.save(syncLog);
    }

    private Set<UUID> findStockChangedVariantIds(OffsetDateTime changedSince, OffsetDateTime changedUntil) {
        Set<UUID> variantIds = new HashSet<>();
        if (changedSince == null) {
            variantIds.addAll(stockReceiveRepository.findConfirmedVariantIdsUpTo(changedUntil));
            variantIds.addAll(inventoryIssueRepository.findConfirmedVariantIdsUpTo(changedUntil));
        } else {
            variantIds.addAll(stockReceiveRepository.findChangedConfirmedVariantIdsBetween(changedSince, changedUntil));
            variantIds.addAll(inventoryIssueRepository.findChangedAppliedVariantIdsBetween(changedSince, changedUntil));
        }
        return variantIds;
    }

    private ChannelSyncDetailResponse detail(Channel channel,
                                             ChannelImportSyncResponse response,
                                             String status,
                                             String message,
                                             long startedAt) {
        return ChannelSyncDetailResponse.builder()
                .channelId(channel.getId())
                .channelName(channel.getDisplayName())
                .platform(channel.getPlatform())
                .sellerId(metadataText(channel, "accountId", "openId"))
                .shopId(metadataText(channel, "shopId"))
                .shopDomain(metadataText(channel, "shopDomain", "shop"))
                .status(status)
                .message(message)
                .productCount(0)
                .variantCount(response == null ? 0 : response.getVariantCount())
                .warehouseCount(response == null ? 0 : response.getWarehouseCount())
                .pushedVariantCount(response == null ? 0 : response.getPushedVariantCount())
                .durationMs(System.currentTimeMillis() - startedAt)
                .build();
    }

    private String metadataText(Channel channel, String... keys) {
        if (channel.getMetadata() == null) {
            return null;
        }
        for (String key : keys) {
            Object value = channel.getMetadata().get(key);
            if (value != null && !value.toString().isBlank()) {
                return value.toString();
            }
        }
        return null;
    }

    private void validateSellerBinding(Channel channel) {
        if (channel.getPlatform() == PlatformType.LAZADA && metadataText(channel, "accountId") == null) {
            throw new AppException(ErrorCode.INVALID_REQUEST,
                    "Kênh Lazada thiếu accountId/sellerId. Hãy kết nối lại Lazada trước khi đồng bộ.");
        }
        if (channel.getPlatform() == PlatformType.TIKTOK
                && metadataText(channel, "shopCipher", "shop_cipher", "cipher") == null) {
            throw new AppException(ErrorCode.INVALID_REQUEST,
                    "Kênh TikTok thiếu shopCipher. Hãy kết nối lại TikTok Shop trước khi đồng bộ.");
        }
        if (channel.getPlatform() == PlatformType.SHOPIFY
                && metadataText(channel, "shopDomain", "shop") == null
                && (channel.getDisplayName() == null || channel.getDisplayName().isBlank())) {
            throw new AppException(ErrorCode.INVALID_REQUEST,
                    "Kênh Shopify thiếu shopDomain. Hãy kết nối lại Shopify trước khi đồng bộ.");
        }
    }
}
