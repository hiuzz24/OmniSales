package fu.osms.sync.lazada.service.impl;

import fu.osms.catalog.entity.Product;
import fu.osms.catalog.entity.ProductImage;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.catalog.repository.ProductImageRepository;
import fu.osms.catalog.repository.ProductVariantRepository;
import fu.osms.channel.dto.response.ChannelImportSyncResponse;
import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelProduct;
import fu.osms.channel.repository.ChannelProductRepository;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.enums.SyncStatus;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.sync.entity.SyncLog;
import fu.osms.sync.lazada.dto.LazadaInventorySyncResult;
import fu.osms.sync.lazada.dto.LazadaSyncTask;
import fu.osms.sync.lazada.service.LazadaChannelSyncService;
import fu.osms.sync.lazada.service.LazadaInventoryUpdateService;
import fu.osms.sync.repository.SyncLogRepository;
import fu.osms.sync.service.PlatformSyncService;
import fu.osms.sync.service.impl.PlatformSyncServiceFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LazadaChannelSyncServiceImpl implements LazadaChannelSyncService {

    private final ChannelRepository channelRepository;
    private final ChannelProductRepository channelProductRepository;
    private final ChannelProductVariantRepository channelProductVariantRepository;
    private final SyncLogRepository syncLogRepository;
    private final LazadaInventoryUpdateService lazadaInventoryUpdateService;
    private final ProductVariantRepository productVariantRepository;
    private final ProductImageRepository productImageRepository;
    private final PlatformSyncServiceFactory platformSyncServiceFactory;

    @Override
    @Transactional
    public ChannelImportSyncResponse syncLocalChanges(LazadaSyncTask task) {
        log.info("[LazadaSync] Received task reason={} channelId={}", task.reason(), task.channelId());
        Channel channel = channelRepository.findById(task.channelId())
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.CHANNEL_NOT_FOUND));

        if (channel.getPlatform() != PlatformType.LAZADA) {
            throw new IllegalArgumentException("Chỉ hỗ trợ đồng bộ cho kênh Lazada.");
        }

        SyncLog syncLog = syncLogRepository.save(SyncLog.builder()
                .channel(channel)
                .jobType("LAZADA_LOCAL_CHANGES_SYNC")
                .status(SyncStatus.PENDING)
                .startedAt(OffsetDateTime.now())
                .build());
        log.info(
                "[LazadaSync] Created sync log syncLogId={} channelId={} channelName={} platform={} status={}",
                syncLog.getId(),
                channel.getId(),
                channel.getDisplayName(),
                channel.getPlatform(),
                syncLog.getStatus()
        );

        int syncedProductCount = 0;
        int failedProductCount = 0;
        int productVariantCount = 0;
        int pushedVariantCount = 0;
        int changedWarehouseCount = 0;

        try {
            OffsetDateTime syncStartedAt = OffsetDateTime.now();
            OffsetDateTime changedSince = channel.getLastSyncedAt();
            log.info(
                    "[LazadaSync] Step 1/4 resolve incremental scope channelId={} changedSince={} mode={}",
                    channel.getId(),
                    changedSince,
                    changedSince == null ? "FULL_BASELINE" : "INCREMENTAL"
            );

            ProductSyncSummary productSummary = syncChangedProducts(channel, changedSince);
            syncedProductCount = productSummary.syncedProductCount();
            failedProductCount = productSummary.failedProductCount();
            productVariantCount = productSummary.variantCount();
            log.info(
                    "[LazadaSync] Step 2/4 pushed local products channelId={} changedSince={} syncedProducts={} failedProducts={} touchedVariants={}",
                    channel.getId(),
                    changedSince,
                    syncedProductCount,
                    failedProductCount,
                    productVariantCount
            );

            LazadaInventorySyncResult inventoryResult =
                    lazadaInventoryUpdateService.syncChangedSellableStock(
                            task.channelId(),
                            changedSince,
                            syncStartedAt,
                            productSummary.changedVariantIds()
                    );
            pushedVariantCount = inventoryResult.pushedVariantCount();
            changedWarehouseCount = inventoryResult.changedWarehouseCount();
            log.info(
                    "[LazadaSync] Step 3/4 pushed local stock channelId={} stockAffectedProducts={} pushedSkuVariants={} changedWarehouses={}",
                    channel.getId(),
                    inventoryResult.affectedProductCount(),
                    pushedVariantCount,
                    changedWarehouseCount
            );

            Map<String, Object> metadata = channel.getMetadata() == null
                    ? new HashMap<>()
                    : new HashMap<>(channel.getMetadata());
            metadata.put("productCount", channelProductRepository.countByChannelIdAndMappingState(channel.getId(), "ACTIVE"));
            metadata.put("skuVariantCount", channelProductVariantRepository.countActiveByChannelId(channel.getId()));
            metadata.putIfAbsent("warehouseCount", 0);
            metadata.put("lastChangedWarehouseCount", changedWarehouseCount);
            metadata.put("lastPushedSkuVariantCount", pushedVariantCount);
            channel.setMetadata(metadata);
            channel.setLastSyncedAt(syncStartedAt);
            channelRepository.save(channel);
            log.info(
                    "[LazadaSync] Step 4/4 updated channel metadata channelId={} lastSyncedAt={} metadata={}",
                    channel.getId(),
                    channel.getLastSyncedAt(),
                    metadata
            );

            int totalItems = syncedProductCount + failedProductCount + changedWarehouseCount + pushedVariantCount;
            syncLog.setStatus(failedProductCount == 0 ? SyncStatus.SYNCED : SyncStatus.FAILED);
            syncLog.setTotalItems(totalItems);
            syncLog.setSuccessCount(syncedProductCount + changedWarehouseCount + pushedVariantCount);
            syncLog.setFailCount(failedProductCount);
            if (failedProductCount > 0) {
                syncLog.setErrorSummary("Có " + failedProductCount + " sản phẩm đồng bộ lên Lazada thất bại.");
            }
            syncLog.setCompletedAt(OffsetDateTime.now());
            syncLogRepository.save(syncLog);
            log.info(
                    "[LazadaSync] Completed syncLogId={} channelId={} totalItems={} successCount={} failCount={}",
                    syncLog.getId(),
                    channel.getId(),
                    syncLog.getTotalItems(),
                    syncLog.getSuccessCount(),
                    syncLog.getFailCount()
            );

            return ChannelImportSyncResponse.builder()
                    .channelId(channel.getId())
                    .syncLogId(syncLog.getId())
                    .productCount(syncedProductCount)
                    .variantCount(productVariantCount)
                    .warehouseCount(changedWarehouseCount)
                    .pushedVariantCount(pushedVariantCount)
                    .status(syncLog.getStatus().name())
                    .message(failedProductCount == 0
                            ? "Đã đồng bộ thay đổi lên Lazada."
                            : "Đồng bộ hoàn tất 1 phần, có sản phẩm bị lỗi.")
                    .build();
        } catch (Exception e) {
            log.error("[LazadaChannelSync] Failed to sync local changes for channel {}", task.channelId(), e);
            syncLog.setStatus(SyncStatus.FAILED);
            syncLog.setTotalItems(syncedProductCount + failedProductCount + changedWarehouseCount + pushedVariantCount);
            syncLog.setSuccessCount(syncedProductCount + changedWarehouseCount + pushedVariantCount);
            syncLog.setFailCount(1);
            syncLog.setErrorSummary(e.getMessage());
            syncLog.setCompletedAt(OffsetDateTime.now());
            syncLogRepository.save(syncLog);
            log.error(
                    "[LazadaSync] Failed syncLogId={} channelId={} totalItems={} successCount={} failCount={} error={}",
                    syncLog.getId(),
                    task.channelId(),
                    syncLog.getTotalItems(),
                    syncLog.getSuccessCount(),
                    syncLog.getFailCount(),
                    e.getMessage()
            );
            throw e;
        }
    }

    private ProductSyncSummary syncChangedProducts(Channel channel, OffsetDateTime changedSince) {
        List<ChannelProduct> channelProducts = changedSince == null
                ? channelProductRepository.findActiveByChannelIdWithProduct(channel.getId())
                : channelProductRepository.findActiveChangedByChannelIdSince(
                        channel.getId(),
                        changedSince,
                        SyncStatus.SYNCED
                );

        if (channelProducts.isEmpty()) {
            log.info(
                    "[LazadaSync] No product changes found channelId={} changedSince={}",
                    channel.getId(),
                    changedSince
            );
            return new ProductSyncSummary(0, 0, 0, Set.of());
        }

        PlatformSyncService platformSyncService = platformSyncServiceFactory.getService(channel.getPlatform());
        Set<UUID> changedVariantIds = new HashSet<>();
        int syncedProductCount = 0;
        int failedProductCount = 0;
        int variantCount = 0;

        for (ChannelProduct channelProduct : channelProducts) {
            Product product = channelProduct.getProduct();
            if (product == null || product.getId() == null) {
                continue;
            }

            List<ProductVariant> variants = productVariantRepository.findByProductIdAndDeletedAtIsNull(product.getId());
            List<ProductImage> images = productImageRepository.findByProductIdOrderByIsPrimaryDescSortOrderAsc(product.getId());
            variantCount += variants.size();

            try {
                boolean success = platformSyncService.syncProduct(product, variants, images, channel, channelProduct);
                if (success) {
                    syncedProductCount++;
                    variants.stream()
                            .map(ProductVariant::getId)
                            .filter(id -> id != null)
                            .forEach(changedVariantIds::add);
                } else {
                    failedProductCount++;
                }
            } catch (Exception e) {
                failedProductCount++;
                log.error(
                        "[LazadaSync] Product sync failed channelId={} productId={} productName={}",
                        channel.getId(),
                        product.getId(),
                        product.getName(),
                        e
                );
                throw new RuntimeException(
                        "Đồng bộ sản phẩm '" + product.getName() + "' lên Lazada thất bại: " + e.getMessage(),
                        e
                );
            }
        }

        return new ProductSyncSummary(syncedProductCount, failedProductCount, variantCount, changedVariantIds);
    }

    private record ProductSyncSummary(int syncedProductCount,
                                      int failedProductCount,
                                      int variantCount,
                                      Set<UUID> changedVariantIds) {
    }
}
