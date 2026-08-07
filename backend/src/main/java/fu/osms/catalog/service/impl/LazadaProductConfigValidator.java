package fu.osms.catalog.service.impl;

import fu.osms.catalog.entity.Product;
import fu.osms.channel.entity.ChannelProduct;
import fu.osms.common.enums.PlatformType;

import java.util.List;
import java.util.Map;

final class LazadaProductConfigValidator extends ProductChannelConfigValidator {

    String shippingValidationError(ChannelProduct channelProduct) {
        if (channelProduct == null || channelProduct.getChannel() == null
                || channelProduct.getChannel().getPlatform() != PlatformType.LAZADA) {
            return null;
        }
        Product product = channelProduct.getProduct();
        if (product == null || product.getWeightGrams() == null || product.getWeightGrams() <= 0) {
            return "Missing Package Weight (kg)";
        }
        Map<String, Object> attributes = product.getAttributes();
        for (String key : List.of("packageWidthCm", "packageHeightCm", "packageLengthCm")) {
            Object value = attributes == null ? null : attributes.get(key);
            if (!isPositiveNumber(value)) {
                return "Missing " + packageFieldLabel(key);
            }
        }
        return null;
    }

    private String packageFieldLabel(String key) {
        return switch (key) {
            case "packageWidthCm" -> "Package Width (cm)";
            case "packageHeightCm" -> "Package Height (cm)";
            case "packageLengthCm" -> "Package Length (cm)";
            default -> key;
        };
    }
}
