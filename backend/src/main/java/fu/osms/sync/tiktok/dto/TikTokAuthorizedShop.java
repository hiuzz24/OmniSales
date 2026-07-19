package fu.osms.sync.tiktok.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TikTokAuthorizedShop {
    private final String shopCipher;
    private final String shopId;
    private final String shopName;
    private final String region;
}
