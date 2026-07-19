package fu.osms.sync.service.impl;

import fu.osms.common.enums.PlatformType;
import fu.osms.sync.service.PlatformSyncService;
import fu.osms.sync.lazada.service.impl.LazadaSyncServiceImpl;
import fu.osms.sync.shopify.impl.ShopifySyncServiceImpl;
import fu.osms.sync.tiktok.impl.TikTokSyncServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PlatformSyncServiceFactory {

    private final ShopifySyncServiceImpl shopifySyncService;
    private final LazadaSyncServiceImpl lazadaSyncService;
    private final TikTokSyncServiceImpl tikTokSyncService;

    public PlatformSyncService getService(PlatformType platform) {
        if (platform == PlatformType.SHOPIFY) {
            return shopifySyncService;
        } else if (platform == PlatformType.LAZADA) {
            return lazadaSyncService;
        } else if (platform == PlatformType.TIKTOK) {
            return tikTokSyncService;
        }
        throw new IllegalArgumentException("Platform is not supported for product sync: " + platform);
    }
}
