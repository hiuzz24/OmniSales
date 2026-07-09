package fu.osms.sync.service.impl;

import fu.osms.catalog.entity.Product;
import fu.osms.catalog.entity.ProductImage;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.catalog.repository.ProductImageRepository;
import fu.osms.catalog.repository.ProductRepository;
import fu.osms.catalog.repository.ProductVariantRepository;
import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelProduct;
import fu.osms.channel.repository.ChannelProductRepository;
import fu.osms.common.enums.SyncStatus;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.sync.dto.SyncResult;
import fu.osms.sync.entity.SyncLog;
import fu.osms.sync.repository.SyncLogRepository;
import fu.osms.sync.service.PlatformSyncService;
import fu.osms.sync.service.ProductSyncOrchestratorService;
import fu.osms.sync.service.SyncAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSyncOrchestratorServiceImpl implements ProductSyncOrchestratorService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductImageRepository productImageRepository;
    private final ChannelProductRepository channelProductRepository;
    private final PlatformSyncServiceFactory platformSyncServiceFactory;
    private final SyncLogRepository syncLogRepository;
    private final SyncAlertService syncAlertService;

    @Override
    @Transactional
    public SyncResult syncProductToAllChannels(UUID productId) {
        Product product = productRepository.findById(productId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        List<ProductVariant> variants = productVariantRepository.findByProductIdAndDeletedAtIsNull(productId);
        List<ProductImage> images = productImageRepository.findByProductIdOrderByIsPrimaryDescSortOrderAsc(productId);
        List<ChannelProduct> channelProducts = channelProductRepository.findByProductIdAndMappingState(productId, "ACTIVE");

        SyncResult result = new SyncResult();
        result.setTotalChannels(channelProducts.size());

        int successCount = 0;
        int failedCount = 0;

        for (ChannelProduct channelProduct : channelProducts) {
            Channel channel = channelProduct.getChannel();
            SyncResult.ChannelSyncDetail detail = SyncResult.ChannelSyncDetail.builder()
                    .channelId(channel.getId().toString())
                    .channelName(channel.getDisplayName())
                    .platform(channel.getPlatform().name())
                    .build();

            SyncLog syncLog = SyncLog.builder()
                    .product(product)
                    .channel(channel)
                    .jobType("PRODUCT_SYNC")
                    .status(SyncStatus.PENDING)
                    .totalItems(1)
                    .build();
            syncLog = syncLogRepository.save(syncLog);

            try {
                PlatformSyncService platformSyncService = platformSyncServiceFactory.getService(channel.getPlatform());
                boolean success = platformSyncService.syncProduct(product, variants, images, channel, channelProduct);

                detail.setSuccess(success);
                if (success) {
                    successCount++;
                    syncLog.setStatus(SyncStatus.SYNCED);
                    syncLog.setSuccessCount(1);
                    syncLog.setCompletedAt(OffsetDateTime.now());
                } else {
                    failedCount++;
                    detail.setErrorMessage(channelProduct.getLastSyncError());
                    syncLog.setStatus(SyncStatus.FAILED);
                    syncLog.setFailCount(1);
                    syncLog.setErrorSummary(channelProduct.getLastSyncError());
                    syncLog.setCompletedAt(OffsetDateTime.now());
                }
            } catch (Exception e) {
                log.error("Failed to sync product {} to channel {}: {}", productId, channel.getDisplayName(), e.getMessage(), e);
                channelProduct.setSyncStatus(SyncStatus.FAILED);
                channelProduct.setLastSyncError(e.getMessage());
                channelProductRepository.save(channelProduct);
                failedCount++;
                detail.setSuccess(false);
                detail.setErrorMessage(e.getMessage());
                
                syncLog.setStatus(SyncStatus.FAILED);
                syncLog.setFailCount(1);
                syncLog.setErrorSummary(e.getMessage());
                syncLog.setCompletedAt(OffsetDateTime.now());
            }

            syncLog = syncLogRepository.save(syncLog);
            if (syncLog.getStatus() == SyncStatus.FAILED) {
                syncAlertService.notifySyncFailure(syncLog);
            }
            result.getDetails().add(detail);
        }

        result.setSuccessCount(successCount);
        result.setFailedCount(failedCount);
        return result;
    }
}
