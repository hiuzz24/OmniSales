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
import fu.osms.sync.lazada.service.LazadaImportSyncService;
import fu.osms.sync.service.ChannelRemoteSyncService;
import fu.osms.sync.shopify.ShopifyImportSyncService;
import fu.osms.sync.tiktok.TikTokImportSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChannelRemoteSyncServiceImpl implements ChannelRemoteSyncService {

    private final ChannelRepository channelRepository;
    private final ChannelConnectionValidator channelConnectionValidator;
    private final LazadaImportSyncService lazadaImportSyncService;
    private final ShopifyImportSyncService shopifyImportSyncService;
    private final TikTokImportSyncService tikTokImportSyncService;
    private final TransactionTemplate transactionTemplate;

    @Override
    public ChannelImportSyncResponse syncAllRemoteChanges(UUID requestedChannelId) {
        channelConnectionValidator.requireConnected(requestedChannelId);

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
                        syncRemoteChanges(channel.getId())
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
                        ? "Đã đồng bộ từ tất cả sàn về ứng dụng."
                        : "Đồng bộ từ sàn hoàn tất một phần. Lỗi " + failedCount + " kênh: " + failedMessages)
                .details(details)
                .build();
    }

    @Override
    @Transactional
    public ChannelImportSyncResponse syncRemoteChanges(UUID channelId) {
        Channel channel = channelConnectionValidator.requireConnected(channelId);

        if (!Boolean.TRUE.equals(channel.getSyncEnabled())) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Kênh đang tắt đồng bộ.");
        }

        validateSellerBinding(channel);

        if (channel.getPlatform() == PlatformType.LAZADA) {
            return lazadaImportSyncService.syncProductsAndWarehouses(channelId);
        }
        if (channel.getPlatform() == PlatformType.SHOPIFY) {
            return shopifyImportSyncService.syncProductsAndInventory(channelId);
        }
        if (channel.getPlatform() == PlatformType.TIKTOK) {
            return tikTokImportSyncService.syncProductsAndInventory(channelId);
        }

        throw new AppException(ErrorCode.INVALID_REQUEST, "Chỉ hỗ trợ đồng bộ từ sàn cho Lazada và Shopify.");
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
