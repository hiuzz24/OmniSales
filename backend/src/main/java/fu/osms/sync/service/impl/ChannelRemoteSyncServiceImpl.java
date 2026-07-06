package fu.osms.sync.service.impl;

import fu.osms.channel.dto.response.ChannelImportSyncResponse;
import fu.osms.channel.entity.Channel;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.sync.lazada.service.LazadaImportSyncService;
import fu.osms.sync.service.ChannelRemoteSyncService;
import fu.osms.sync.shopify.ShopifyImportSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChannelRemoteSyncServiceImpl implements ChannelRemoteSyncService {

    private final ChannelRepository channelRepository;
    private final LazadaImportSyncService lazadaImportSyncService;
    private final ShopifyImportSyncService shopifyImportSyncService;

    @Override
    @Transactional
    public ChannelImportSyncResponse syncRemoteChanges(UUID channelId) {
        Channel channel = channelRepository.findById(channelId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.CHANNEL_NOT_FOUND));

        if (!Boolean.TRUE.equals(channel.getSyncEnabled())) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Kênh đang tắt đồng bộ.");
        }

        if (channel.getPlatform() == PlatformType.LAZADA) {
            return lazadaImportSyncService.syncProductsAndWarehouses(channelId);
        }
        if (channel.getPlatform() == PlatformType.SHOPIFY) {
            return shopifyImportSyncService.syncProductsAndInventory(channelId);
        }

        throw new AppException(ErrorCode.INVALID_REQUEST, "Chỉ hỗ trợ đồng bộ từ sàn cho Lazada và Shopify.");
    }
}
