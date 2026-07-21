package fu.osms.sync.service.impl;

import fu.osms.catalog.entity.Product;
import fu.osms.catalog.entity.ProductImage;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.catalog.repository.ProductImageRepository;
import fu.osms.catalog.repository.ProductVariantRepository;
import fu.osms.channel.dto.response.ChannelImportSyncResponse;
import fu.osms.channel.dto.response.ChannelSyncDetailResponse;
import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelProduct;
import fu.osms.channel.repository.ChannelProductRepository;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.channel.service.ChannelConnectionValidator;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.enums.SyncStatus;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.inventory.repository.InventoryIssueRepository;
import fu.osms.inventory.repository.StockReceiveRepository;
import fu.osms.sync.entity.SyncLog;
import fu.osms.sync.lazada.dto.LazadaSyncTask;
import fu.osms.sync.lazada.service.LazadaSyncTaskDispatcher;
import fu.osms.sync.repository.SyncLogRepository;
import fu.osms.sync.service.ChannelLocalSyncService;
import fu.osms.sync.service.MarketplaceWarehouseConsistencyService;
import fu.osms.sync.service.PlatformSyncService;
import fu.osms.sync.shopify.ShopifyInventoryUpdateService;
import fu.osms.sync.tiktok.TikTokInventoryUpdateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
    private final ChannelProductRepository channelProductRepository;
    private final ChannelProductVariantRepository channelProductVariantRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductImageRepository productImageRepository;
    private final PlatformSyncServiceFactory platformSyncServiceFactory;
    private final SyncLogRepository syncLogRepository;
    private final LazadaSyncTaskDispatcher lazadaSyncTaskDispatcher;
    private final ShopifyInventoryUpdateService shopifyInventoryUpdateService;
    private final TikTokInventoryUpdateService tikTokInventoryUpdateService;
    private final StockReceiveRepository stockReceiveRepository;
    private final InventoryIssueRepository inventoryIssueRepository;
    private final MarketplaceWarehouseConsistencyService marketplaceWarehouseConsistencyService;
    private final TransactionTemplate transactionTemplate;

    @Override
    public ChannelImportSyncResponse syncAllLocalChanges(UUID requestedChannelId) {
        channelConnectionValidator.requireConnected(requestedChannelId);

        marketplaceWarehouseConsistencyService.validateConnectedPrimaryWarehouses();

        int productCount = 0;
        int variantCount = 0;
        int warehouseCount = 0;
        int pushedVariantCount = 0;
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
                productCount += response.getProductCount();
                variantCount += response.getVariantCount();
                warehouseCount += response.getWarehouseCount();
                pushedVariantCount += response.getPushedVariantCount();
                details.add(detail(channel, response, response.getStatus(), response.getMessage(), startedAt));
            } catch (Exception e) {
                failedCount++;
                failedMessages.add(channel.getDisplayName() + ": " + e.getMessage());
                log.error("[ChannelLocalSync] Sync all failed for channelId={}", channel.getId(), e);
                details.add(detail(channel, null, SyncStatus.FAILED.name(), e.getMessage(), startedAt));
            }
        }

        return ChannelImportSyncResponse.builder()
                .channelId(requestedChannelId)
                .productCount(productCount)
                .variantCount(variantCount)
                .warehouseCount(warehouseCount)
                .pushedVariantCount(pushedVariantCount)
                .status(failedCount == 0 ? SyncStatus.SYNCED.name() : SyncStatus.FAILED.name())
                .message(failedCount == 0
                        ? "Đã đồng bộ từ ứng dụng lên tất cả sàn đã liên kết."
                        : "Đồng bộ hoàn tất một phần. Lỗi " + failedCount + " kênh: " + failedMessages)
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

        if (channel.getPlatform() == PlatformType.LAZADA) {
            return lazadaSyncTaskDispatcher.dispatch(LazadaSyncTask.localChanges(channelId));
        }
        if (channel.getPlatform() == PlatformType.TIKTOK) {
            return syncTikTokInventory(channel);
        }
        if (channel.getPlatform() != PlatformType.SHOPIFY) {
            throw new IllegalArgumentException("Chỉ hỗ trợ đồng bộ thủ công cho Lazada và Shopify.");
        }

        return syncShopifyLocalChanges(channel);
    }

    private ChannelImportSyncResponse syncTikTokInventory(Channel channel) {
        SyncLog syncLog = syncLogRepository.save(SyncLog.builder()
                .channel(channel)
                .jobType("TIKTOK_INVENTORY_PUSH_SYNC")
                .status(SyncStatus.PENDING)
                .startedAt(OffsetDateTime.now())
                .build());
        try {
            int pushedVariantCount = tikTokInventoryUpdateService.pushAvailableStock(channel.getId());
            OffsetDateTime syncedAt = OffsetDateTime.now();
            channel.setLastSyncedAt(syncedAt);
            channelRepository.save(channel);
            syncLog.setStatus(SyncStatus.SYNCED);
            syncLog.setTotalItems(pushedVariantCount);
            syncLog.setSuccessCount(pushedVariantCount);
            syncLog.setFailCount(0);
            syncLog.setCompletedAt(syncedAt);
            syncLogRepository.save(syncLog);
            return ChannelImportSyncResponse.builder()
                    .channelId(channel.getId())
                    .syncLogId(syncLog.getId())
                    .productCount(0)
                    .variantCount(pushedVariantCount)
                    .warehouseCount(0)
                    .pushedVariantCount(pushedVariantCount)
                    .status(SyncStatus.SYNCED.name())
                    .message("Đã đẩy tồn kho từ ứng dụng lên TikTok Shop.")
                    .build();
        } catch (Exception e) {
            syncLog.setStatus(SyncStatus.FAILED);
            syncLog.setFailCount(1);
            syncLog.setErrorSummary(e.getMessage());
            syncLog.setCompletedAt(OffsetDateTime.now());
            syncLogRepository.save(syncLog);
            throw e;
        }
    }

    private ChannelImportSyncResponse syncShopifyLocalChanges(Channel channel) {
        SyncLog syncLog = syncLogRepository.save(SyncLog.builder()
                .channel(channel)
                .jobType("SHOPIFY_LOCAL_CHANGES_SYNC")
                .status(SyncStatus.PENDING)
                .startedAt(OffsetDateTime.now())
                .build());

        int syncedProductCount = 0;
        int failedProductCount = 0;
        int variantCount = 0;
        int pushedVariantCount = 0;

        try {
            OffsetDateTime syncStartedAt = OffsetDateTime.now();
            OffsetDateTime changedSince = channel.getLastSyncedAt();
            Set<UUID> stockChangedVariantIds = findStockChangedVariantIds(changedSince, syncStartedAt);
            List<ChannelProduct> channelProducts = changedSince == null
                    ? channelProductRepository.findActiveByChannelIdWithProduct(channel.getId())
                    : channelProductRepository.findActiveChangedByChannelIdSince(
                            channel.getId(),
                            changedSince,
                            SyncStatus.SYNCED
                    );
            if (changedSince != null && !stockChangedVariantIds.isEmpty()) {
                Map<UUID, ChannelProduct> scopedProducts = new LinkedHashMap<>();
                for (ChannelProduct channelProduct : channelProducts) {
                    scopedProducts.put(channelProduct.getId(), channelProduct);
                }
                channelProductRepository.findActiveByChannelIdAndVariantIdIn(channel.getId(), stockChangedVariantIds)
                        .forEach(channelProduct -> scopedProducts.put(channelProduct.getId(), channelProduct));
                channelProducts = new ArrayList<>(scopedProducts.values());
            }

            PlatformSyncService platformSyncService = platformSyncServiceFactory.getService(channel.getPlatform());
            Set<UUID> changedVariantIds = new HashSet<>(stockChangedVariantIds);
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
                            "[ShopifyLocalSync] Product sync failed channelId={} productId={} productName={}",
                            channel.getId(),
                            product.getId(),
                            product.getName(),
                            e
                    );
                }
            }

            pushedVariantCount = shopifyInventoryUpdateService.syncChangedAvailableStock(
                    channel.getId(),
                    changedSince,
                    syncStartedAt,
                    changedVariantIds
            );

            Map<String, Object> metadata = channel.getMetadata() == null
                    ? new HashMap<>()
                    : new HashMap<>(channel.getMetadata());
            metadata.put("productCount", channelProductRepository.countByChannelIdAndMappingState(channel.getId(), "ACTIVE"));
            metadata.put("skuVariantCount", channelProductVariantRepository.countActiveByChannelId(channel.getId()));
            metadata.put("lastPushedProductCount", syncedProductCount);
            metadata.put("lastPushedSkuVariantCount", pushedVariantCount);
            channel.setMetadata(metadata);
            channel.setLastSyncedAt(syncStartedAt);
            channelRepository.save(channel);

            syncLog.setStatus(failedProductCount == 0 ? SyncStatus.SYNCED : SyncStatus.FAILED);
            syncLog.setTotalItems(syncedProductCount + failedProductCount + pushedVariantCount);
            syncLog.setSuccessCount(syncedProductCount + pushedVariantCount);
            syncLog.setFailCount(failedProductCount);
            if (failedProductCount > 0) {
                syncLog.setErrorSummary("Có " + failedProductCount + " sản phẩm đồng bộ lên Shopify thất bại.");
            }
            syncLog.setCompletedAt(OffsetDateTime.now());
            syncLogRepository.save(syncLog);

            return ChannelImportSyncResponse.builder()
                    .channelId(channel.getId())
                    .syncLogId(syncLog.getId())
                    .productCount(syncedProductCount)
                    .variantCount(variantCount)
                    .warehouseCount(0)
                    .pushedVariantCount(pushedVariantCount)
                    .status(syncLog.getStatus().name())
                    .message(failedProductCount == 0
                            ? "Đã đồng bộ thay đổi từ ứng dụng lên Shopify."
                            : "Đồng bộ hoàn tất một phần, có sản phẩm bị lỗi.")
                    .build();
        } catch (Exception e) {
            syncLog.setStatus(SyncStatus.FAILED);
            syncLog.setTotalItems(syncedProductCount + failedProductCount + pushedVariantCount);
            syncLog.setSuccessCount(syncedProductCount + pushedVariantCount);
            syncLog.setFailCount(Math.max(1, failedProductCount));
            syncLog.setErrorSummary(e.getMessage());
            syncLog.setCompletedAt(OffsetDateTime.now());
            syncLogRepository.save(syncLog);
            throw e;
        }
    }

    private Set<UUID> findStockChangedVariantIds(OffsetDateTime changedSince, OffsetDateTime changedUntil) {
        if (changedSince == null) {
            return Set.of();
        }
        Set<UUID> variantIds = new HashSet<>();
        variantIds.addAll(stockReceiveRepository.findChangedConfirmedVariantIdsBetween(changedSince, changedUntil));
        variantIds.addAll(inventoryIssueRepository.findChangedAppliedVariantIdsBetween(changedSince, changedUntil));
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
                .productCount(response == null ? 0 : response.getProductCount())
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
        if (channel.getPlatform() == PlatformType.TIKTOK && metadataText(channel, "shopCipher", "shop_cipher", "cipher") == null) {
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
