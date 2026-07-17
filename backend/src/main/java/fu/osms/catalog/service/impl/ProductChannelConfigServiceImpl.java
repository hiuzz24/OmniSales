package fu.osms.catalog.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.catalog.dto.request.ChannelConfigRequest;
import fu.osms.catalog.dto.response.ChannelProductConfigResponse;
import fu.osms.catalog.dto.response.PlatformAttributeResponse;
import fu.osms.catalog.entity.Product;
import fu.osms.catalog.service.PlatformLookupService;
import fu.osms.catalog.service.ProductChannelConfigService;
import fu.osms.channel.entity.ChannelProduct;
import fu.osms.channel.repository.ChannelProductRepository;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.enums.SyncStatus;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ProductChannelConfigServiceImpl implements ProductChannelConfigService {

    private static final String CONFIG_KEY = "platformConfig";
    private static final Set<String> LAZADA_SYSTEM_ATTRIBUTES = Set.of(
            "sellersku", "seller_sku", "price", "supply_price", "quantity",
            "package_weight", "package_width", "package_height", "package_length",
            "brand", "brand_id"
    );
    private static final Set<String> SUPPORTED_VARIANT_OPTIONS = Set.of("Size", "Màu");

    private static final Set<String> OPTIONAL_LAZADA_SPECIFICATION_ATTRIBUTES = Set.of(
            "clothing_material", "pattern", "neckline", "clothing_style", "details",
            "sleeves_type", "sleeve_type"
    );
    private static final Set<String> TIKTOK_LISTING_ATTRIBUTES = Set.of("100149", "101489", "101490");

    private final ChannelProductRepository channelProductRepository;
    private final PlatformLookupServiceFactory lookupServiceFactory;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public ChannelProductConfigResponse getConfig(UUID productId, UUID channelId) {
        ChannelProduct channelProduct = findMapping(productId, channelId);
        return response(channelProduct);
    }

    @Override
    @Transactional
    public ChannelProductConfigResponse updateConfig(UUID productId, UUID channelId, ChannelConfigRequest request) {
        if (request == null) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Channel configuration is required");
        }
        if (request.getChannelId() != null && !channelId.equals(request.getChannelId())) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Channel configuration does not match path channel");
        }
        ChannelProduct channelProduct = findMapping(productId, channelId);
        applyConfig(channelProduct, request);
        channelProductRepository.save(channelProduct);
        return response(channelProduct);
    }

    @Override
    public void applyInitialConfig(ChannelProduct channelProduct, ChannelConfigRequest request) {
        if (request == null) {
            initializeWithoutConfig(channelProduct);
            return;
        }
        applyConfig(channelProduct, request);
    }

    @Override
    public boolean isReady(ChannelProduct channelProduct) {
        if (channelProduct == null || channelProduct.getChannel() == null) return false;
        if (channelProduct.getChannel().getPlatform() == PlatformType.SHOPIFY) return true;
        if (!Boolean.TRUE.equals(config(channelProduct).get("readyToSync"))) return false;
        return shippingValidationError(channelProduct) == null
                && platformProductValidationError(channelProduct) == null;
    }

    @Override
    public String configurationError(ChannelProduct channelProduct) {
        Object value = config(channelProduct).get("configurationError");
        if (value != null) return String.valueOf(value);
        String shippingError = shippingValidationError(channelProduct);
        return shippingError != null ? shippingError : platformProductValidationError(channelProduct);
    }

    private void initializeWithoutConfig(ChannelProduct channelProduct) {
        Map<String, Object> config = config(channelProduct);
        if (channelProduct.getChannel().getPlatform() == PlatformType.SHOPIFY) {
            config.put("readyToSync", true);
            config.remove("configurationError");
        } else {
            config.put("readyToSync", false);
            config.put("configurationError", "Missing platform category configuration");
        }
        persistConfig(channelProduct, config);
    }

    private void applyConfig(ChannelProduct channelProduct, ChannelConfigRequest request) {
        PlatformType platform = channelProduct.getChannel().getPlatform();
        if (platform == PlatformType.SHOPIFY) {
            Map<String, Object> config = config(channelProduct);
            config.put("readyToSync", true);
            config.remove("configurationError");
            persistConfig(channelProduct, config);
            return;
        }
        if (platform != PlatformType.LAZADA && platform != PlatformType.TIKTOK) {
            initializeWithoutConfig(channelProduct);
            return;
        }

        Map<String, Object> nextConfig = new HashMap<>();
        putIfText(nextConfig, "categoryId", request.getCategoryId());
        putIfText(nextConfig, "categoryName", request.getCategoryName());
        putIfText(nextConfig, "categorySource", request.getCategorySource());
        if (Boolean.TRUE.equals(request.getCategoryConfirmed())) nextConfig.put("categoryConfirmed", true);
        if (platform == PlatformType.LAZADA || platform == PlatformType.TIKTOK) {
            putIfText(nextConfig, "brandId", request.getBrandId());
            putIfText(nextConfig, "brandName", request.getBrandName());
        }
        if (platform == PlatformType.TIKTOK) {
            nextConfig.put("categoryVersion", textOrDefault(request.getCategoryVersion(), "v1"));
            putIfText(nextConfig, "sizeChartImageUrl", request.getSizeChartImageUrl());
        }

        PlatformLookupService lookup = lookupServiceFactory.get(platform);
        String categoryId = request.getCategoryId();
        if (categoryId == null || categoryId.isBlank()) {
            markNotReady(nextConfig, "Missing " + platform.name() + " category");
            persistConfig(channelProduct, nextConfig);
            return;
        }
        String categoryVersion = stringValue(nextConfig.get("categoryVersion"));
        boolean platformSuggestion = "PLATFORM_SUGGESTION".equals(request.getCategorySource())
                && Boolean.TRUE.equals(request.getCategoryConfirmed());
        if (!platformSuggestion && !lookup.isLeafCategory(channelProduct.getChannel().getId(), categoryId, categoryVersion)) {
            markNotReady(nextConfig, "Platform category must be an available leaf category");
            persistConfig(channelProduct, nextConfig);
            return;
        }

        if (platform == PlatformType.LAZADA && isEmpty(nextConfig.get("brandId"))) {
            markNotReady(nextConfig, "Missing Lazada brand");
            persistConfig(channelProduct, nextConfig);
            return;
        }

        List<PlatformAttributeResponse> schema = lookup.getAttributes(channelProduct.getChannel().getId(), categoryId, categoryVersion);
        Map<String, Object> validAttributes = filterAttributes(request.getAttributes(), schema, platform);
        Map<String, String> validBindings = filterBindings(request.getVariantAttributeBindings(), schema);
        nextConfig.put("attributes", validAttributes);
        nextConfig.put("variantAttributeBindings", validBindings);

        String validationError = validationError(schema, validAttributes, validBindings, platform);
        if (validationError == null && platform == PlatformType.TIKTOK && isEmpty(nextConfig.get("sizeChartImageUrl"))) {
            validationError = "Missing TikTok size chart image";
        }
        if (validationError != null) {
            markNotReady(nextConfig, validationError);
        } else {
            nextConfig.put("readyToSync", true);
            nextConfig.remove("configurationError");
            channelProduct.setSyncStatus(SyncStatus.PENDING);
            channelProduct.setLastSyncError(null);
        }
        persistConfig(channelProduct, nextConfig);
    }

    private String validationError(List<PlatformAttributeResponse> schema,
                                   Map<String, Object> attributes,
                                   Map<String, String> bindings,
                                   PlatformType platform) {
        for (PlatformAttributeResponse attribute : schema) {
            if (isSystemManaged(attribute)) continue;
            boolean requiredForListing = Boolean.TRUE.equals(attribute.getRequired())
                    || (platform == PlatformType.TIKTOK && isTikTokListingAttribute(attribute));
            if (!requiredForListing) continue;
            if (Boolean.TRUE.equals(attribute.getSaleProperty())) {
                String binding = firstValue(bindings, attribute.getId(), attribute.getName());
                if (binding == null || binding.isBlank()) {
                    return "Missing variant attribute binding: " + displayName(attribute);
                }
                if (!SUPPORTED_VARIANT_OPTIONS.contains(binding)) {
                    return "Unsupported variant attribute binding: " + displayName(attribute);
                }
            } else if (isEmpty(firstValue(attributes, attribute.getId(), attribute.getName()))) {
                return "Missing required attribute: " + displayName(attribute);
            }
        }
        return null;
    }

    private Map<String, Object> filterAttributes(Map<String, Object> attributes,
                                                 List<PlatformAttributeResponse> schema,
                                                 PlatformType platform) {
        if (attributes == null || attributes.isEmpty()) return new HashMap<>();
        Set<String> allowed = new HashSet<>();
        for (PlatformAttributeResponse attribute : schema) {
            boolean includeAttribute = Boolean.TRUE.equals(attribute.getRequired())
                    || isOptionalSpecification(attribute)
                    || (platform == PlatformType.TIKTOK && isTikTokListingAttribute(attribute));
            if (!Boolean.TRUE.equals(attribute.getSaleProperty())
                    && !isSystemManaged(attribute)
                    && includeAttribute) {
                allowed.add(attribute.getName());
                allowed.add(attribute.getId());
            }
        }
        Map<String, Object> result = new HashMap<>();
        attributes.forEach((key, value) -> {
            if (allowed.contains(key) && !isEmpty(value)) result.put(key, value);
        });
        return result;
    }

    private Map<String, String> filterBindings(Map<String, String> bindings, List<PlatformAttributeResponse> schema) {
        if (bindings == null || bindings.isEmpty()) return new HashMap<>();
        Set<String> salePropertyNames = new HashSet<>();
        for (PlatformAttributeResponse attribute : schema) {
            if (Boolean.TRUE.equals(attribute.getRequired())
                    && Boolean.TRUE.equals(attribute.getSaleProperty())
                    && !isSystemManaged(attribute)) {
                salePropertyNames.add(attribute.getName());
                salePropertyNames.add(attribute.getId());
            }
        }
        Map<String, String> result = new HashMap<>();
        bindings.forEach((key, value) -> {
            if (salePropertyNames.contains(key) && SUPPORTED_VARIANT_OPTIONS.contains(value)) result.put(key, value);
        });
        return result;
    }

    private ChannelProductConfigResponse response(ChannelProduct channelProduct) {
        Map<String, Object> config = config(channelProduct);
        return ChannelProductConfigResponse.builder()
                .channelId(channelProduct.getChannel().getId())
                .channelName(channelProduct.getChannel().getDisplayName())
                .platform(channelProduct.getChannel().getPlatform().name())
                .categoryId(stringValue(config.get("categoryId")))
                .categoryName(stringValue(config.get("categoryName")))
                .categorySource(stringValue(config.get("categorySource")))
                .categoryConfirmed(Boolean.TRUE.equals(config.get("categoryConfirmed")))
                .categoryVersion(stringValue(config.get("categoryVersion")))
                .brandId(stringValue(config.get("brandId")))
                .brandName(stringValue(config.get("brandName")))
                .attributes(mapValue(config.get("attributes")))
                .variantAttributeBindings(stringMapValue(config.get("variantAttributeBindings")))
                .readyToSync(isReady(channelProduct))
                .configurationError(configurationError(channelProduct))
                .build();
    }

    private ChannelProduct findMapping(UUID productId, UUID channelId) {
        return channelProductRepository.findByProductIdAndChannelId(productId, channelId)
                .filter(value -> "ACTIVE".equals(value.getMappingState()))
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Active product channel mapping not found"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> config(ChannelProduct channelProduct) {
        Map<String, Object> metadata = channelProduct.getMetadata() == null
                ? new HashMap<>()
                : new HashMap<>(channelProduct.getMetadata());
        Object value = metadata.get(CONFIG_KEY);
        if (value instanceof Map<?, ?> map) {
            return objectMapper.convertValue(map, Map.class);
        }
        return new HashMap<>();
    }

    private void persistConfig(ChannelProduct channelProduct, Map<String, Object> config) {
        Map<String, Object> metadata = channelProduct.getMetadata() == null
                ? new HashMap<>()
                : new HashMap<>(channelProduct.getMetadata());
        metadata.put(CONFIG_KEY, config);
        channelProduct.setMetadata(metadata);
    }

    private void markNotReady(Map<String, Object> config, String error) {
        config.put("readyToSync", false);
        config.put("configurationError", error);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> map ? objectMapper.convertValue(map, Map.class) : new HashMap<>();
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> stringMapValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) return new HashMap<>();
        Map<String, String> result = new HashMap<>();
        map.forEach((key, entryValue) -> result.put(String.valueOf(key), String.valueOf(entryValue)));
        return result;
    }

    private boolean isEmpty(Object value) {
        if (value == null) return true;
        if (value instanceof String text) return text.isBlank();
        if (value instanceof List<?> list) return list.isEmpty();
        return false;
    }

    private String shippingValidationError(ChannelProduct channelProduct) {
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

    private String platformProductValidationError(ChannelProduct channelProduct) {
        if (channelProduct == null || channelProduct.getProduct() == null
                || channelProduct.getChannel() == null
                || channelProduct.getChannel().getPlatform() != PlatformType.TIKTOK) {
            return null;
        }
        String name = channelProduct.getProduct().getName();
        int length = name == null ? 0 : name.trim().length();
        if (length < 25 || length > 255) {
            return "TikTok product name must be between 25 and 255 characters";
        }
        return null;
    }

    private boolean isPositiveNumber(Object value) {
        if (value == null || value.toString().isBlank()) return false;
        try {
            return new java.math.BigDecimal(value.toString()).signum() > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String packageFieldLabel(String key) {
        return switch (key) {
            case "packageWidthCm" -> "Package Width (cm)";
            case "packageHeightCm" -> "Package Height (cm)";
            case "packageLengthCm" -> "Package Length (cm)";
            default -> key;
        };
    }

    private boolean isSystemManaged(PlatformAttributeResponse attribute) {
        String name = attribute.getName();
        return name != null && LAZADA_SYSTEM_ATTRIBUTES.contains(name.toLowerCase(Locale.ROOT));
    }

    private boolean isOptionalSpecification(PlatformAttributeResponse attribute) {
        String name = attribute.getName();
        return name != null && OPTIONAL_LAZADA_SPECIFICATION_ATTRIBUTES.contains(name.toLowerCase(Locale.ROOT));
    }

    private boolean isTikTokListingAttribute(PlatformAttributeResponse attribute) {
        return attribute.getId() != null && TIKTOK_LISTING_ATTRIBUTES.contains(attribute.getId());
    }

    private String displayName(PlatformAttributeResponse attribute) {
        return textOrDefault(attribute.getLabel(), attribute.getName());
    }

    private <T> T firstValue(Map<String, T> values, String... keys) {
        if (values == null) return null;
        for (String key : keys) {
            if (key != null && values.containsKey(key)) return values.get(key);
        }
        return null;
    }

    private void putIfText(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) target.put(key, value);
    }

    private String textOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }


    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
