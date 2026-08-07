package fu.osms.catalog.service.impl;

import fu.osms.catalog.dto.TikTokProductTitleInput;
import fu.osms.catalog.dto.TikTokProductTitleResult;
import fu.osms.catalog.entity.Product;
import fu.osms.catalog.service.TikTokProductTitleResolver;
import fu.osms.channel.entity.ChannelProduct;
import fu.osms.common.enums.PlatformType;

import java.util.Map;

final class TikTokProductConfigValidator extends ProductChannelConfigValidator {

    private final TikTokProductTitleResolver titleResolver;

    TikTokProductConfigValidator(TikTokProductTitleResolver titleResolver) {
        this.titleResolver = titleResolver;
    }

    String productValidationError(ChannelProduct channelProduct, Map<String, Object> config) {
        if (channelProduct == null || channelProduct.getProduct() == null
                || channelProduct.getChannel() == null
                || channelProduct.getChannel().getPlatform() != PlatformType.TIKTOK) {
            return null;
        }
        TikTokProductTitleResult result = resolveTitle(channelProduct.getProduct(), config);
        return result.valid() ? null : result.validationError();
    }

    TikTokProductTitleResult resolveTitle(Product product, Map<String, Object> config) {
        return titleResolver.resolve(new TikTokProductTitleInput(
                stringValue(config.get("listingTitle")),
                product == null ? null : product.getName(),
                stringValue(config.get("categoryName")),
                stringValue(config.get("brandName")),
                product == null ? null : product.getDescription()
        ));
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
