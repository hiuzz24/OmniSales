package fu.osms.sync.service;

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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSyncOrchestratorService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductImageRepository productImageRepository;
    private final ChannelProductRepository channelProductRepository;
    private final PlatformSyncServiceFactory platformSyncServiceFactory;

    @Transactional
    public SyncResult syncProductToAllChannels(UUID productId) {
        Product product = productRepository.findById(productId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        List<ProductVariant> variants = productVariantRepository.findByProductIdAndDeletedAtIsNull(productId);
        List<ProductImage> images = productImageRepository.findByProductIdOrderBySortOrderAsc(productId);
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

            try {
                PlatformSyncService platformSyncService = platformSyncServiceFactory.getService(channel.getPlatform());
                boolean success = platformSyncService.syncProduct(product, variants, images, channel, channelProduct);

                detail.setSuccess(success);
                if (success) {
                    successCount++;
                } else {
                    failedCount++;
                    detail.setErrorMessage(channelProduct.getLastSyncError());
                }
            } catch (Exception e) {
                log.error("Failed to sync product {} to channel {}: {}", productId, channel.getDisplayName(), e.getMessage(), e);
                channelProduct.setSyncStatus(SyncStatus.FAILED);
                channelProduct.setLastSyncError(e.getMessage());
                channelProductRepository.save(channelProduct);
                failedCount++;
                detail.setSuccess(false);
                detail.setErrorMessage(e.getMessage());
            }

            result.getDetails().add(detail);
        }

        result.setSuccessCount(successCount);
        result.setFailedCount(failedCount);
        return result;
    }
}
