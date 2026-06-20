package fu.osms.sync.service;

import fu.osms.common.enums.PlatformType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PlatformSyncServiceFactory {

    private final ShopifySyncService shopifySyncService;

    public PlatformSyncService getService(PlatformType platform) {
        if (platform == PlatformType.SHOPIFY) {
            return shopifySyncService;
        }
        throw new IllegalArgumentException("Platform is not supported for product sync: " + platform);
    }
}
