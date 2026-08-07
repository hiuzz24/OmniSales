package fu.osms.catalog.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.catalog.dto.request.ChannelConfigRequest;
import fu.osms.catalog.dto.response.ChannelProductConfigResponse;
import fu.osms.catalog.dto.response.PlatformAttributeOptionResponse;
import fu.osms.catalog.dto.response.PlatformAttributeResponse;
import fu.osms.catalog.dto.TikTokProductTitleInput;
import fu.osms.catalog.dto.TikTokProductTitleResult;
import fu.osms.catalog.entity.Product;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.catalog.repository.ProductVariantRepository;
import fu.osms.catalog.service.PlatformLookupService;
import fu.osms.catalog.service.ProductChannelConfigService;
import fu.osms.catalog.service.TikTokProductTitleResolver;
import fu.osms.channel.entity.ChannelProduct;
import fu.osms.channel.repository.ChannelProductRepository;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.enums.SyncStatus;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
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
    private static final Set<String> OPTIONAL_LAZADA_SPECIFICATION_ATTRIBUTES = Set.of(
            "clothing_material", "pattern", "neckline", "clothing_style", "details",
            "sleeves_type", "sleeve_type"
    );
    private static final Set<String> TIKTOK_LISTING_ATTRIBUTES = Set.of("100149", "101489", "101490");

    private final ChannelProductRepository channelProductRepository;
    private final ProductVariantRepository productVariantRepository;
    private final PlatformLookupServiceFactory lookupServiceFactory;
    private final ObjectMapper objectMapper;
    private final TikTokProductTitleResolver tikTokProductTitleResolver;

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

        Map<String, Object> previousConfig = config(channelProduct);
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
            nextConfig.put("categoryVersion", textOrDefault(request.getCategoryVersion(), "v2"));
            putIfText(nextConfig, "sizeChartImageUrl", request.getSizeChartImageUrl());
            putIfText(nextConfig, "listingTitle", request.getListingTitle());
        }

        PlatformLookupService lookup = lookupServiceFactory.get(platform);
        String categoryId = request.getCategoryId();
        if (categoryId == null || categoryId.isBlank()) {
            markNotReady(nextConfig, "Missing " + platform.name() + " category");
            persistConfigChange(channelProduct, previousConfig, nextConfig);
            return;
        }
        String categoryVersion = stringValue(nextConfig.get("categoryVersion"));
        boolean platformSuggestion = "PLATFORM_SUGGESTION".equals(request.getCategorySource())
                && Boolean.TRUE.equals(request.getCategoryConfirmed());
        if (!platformSuggestion && !lookup.isLeafCategory(channelProduct.getChannel().getId(), categoryId, categoryVersion)) {
            markNotReady(nextConfig, "Platform category must be an available leaf category");
            persistConfigChange(channelProduct, previousConfig, nextConfig);
            return;
        }

        if (platform == PlatformType.LAZADA && isEmpty(nextConfig.get("brandId"))) {
            markNotReady(nextConfig, "Missing Lazada brand");
            persistConfigChange(channelProduct, previousConfig, nextConfig);
            return;
        }

        List<PlatformAttributeResponse> schema = lookup.getAttributes(channelProduct.getChannel().getId(), categoryId, categoryVersion);
        Map<String, Object> validAttributes = filterAttributes(request.getAttributes(), schema, platform);
        Map<String, Map<String, String>> validValueMappings = platform == PlatformType.LAZADA
                ? filterVariantValueMappings(request.getVariantAttributeValueMappings(), schema)
                : Collections.emptyMap();
        nextConfig.put("attributes", validAttributes);
        if (platform == PlatformType.LAZADA) {
            nextConfig.put("variantAttributeValueMappings", validValueMappings);
        }

        String validationError = validationError(
                schema, validAttributes, validValueMappings, platform, channelProduct);
        if (validationError == null && platform == PlatformType.TIKTOK) {
            String sizeChartImageUrl = stringValue(nextConfig.get("sizeChartImageUrl"));
            if (sizeChartImageUrl == null || sizeChartImageUrl.isBlank()) {
                validationError = "Missing TikTok size chart image";
            } else if (!isHttpUrl(sizeChartImageUrl)) {
                validationError = "TikTok size chart image URL must start with http:// or https://";
            }
        }
        if (validationError == null && platform == PlatformType.TIKTOK) {
            TikTokProductTitleResult titleResult = resolveTikTokTitle(channelProduct.getProduct(), nextConfig);
            if (!titleResult.valid()) validationError = titleResult.validationError();
        }
        if (validationError != null) {
            markNotReady(nextConfig, validationError);
        } else {
            nextConfig.put("readyToSync", true);
            nextConfig.remove("configurationError");
        }
        persistConfigChange(channelProduct, previousConfig, nextConfig);
    }

    private boolean isHttpUrl(String value) {
        return configValidator().isHttpUrl(value);
    }

    private String validationError(List<PlatformAttributeResponse> schema,
                                   Map<String, Object> attributes,
                                   Map<String, Map<String, String>> valueMappings,
                                   PlatformType platform,
                                   ChannelProduct channelProduct) {
        List<ProductVariant> variants = productVariantRepository
                .findByProductIdAndDeletedAtIsNull(channelProduct.getProduct().getId()).stream()
                .filter(variant -> !Boolean.FALSE.equals(variant.getIsActive()))
                .toList();
        if (platform == PlatformType.TIKTOK && variants.size() > 1) {
            boolean hasSalesAttribute = schema.stream()
                    .filter(attribute -> Boolean.TRUE.equals(attribute.getSaleProperty()))
                    .map(this::tikTokVariantOptionKey)
                    .filter(Objects::nonNull)
                    .anyMatch(optionKey -> variants.stream().anyMatch(variant ->
                            !isEmpty(variant.getOptionValues() == null
                                    ? null : variant.getOptionValues().get(optionKey))));
            if (!hasSalesAttribute) {
                return "TikTok product variants require Size or Màu values";
            }
        }
        for (PlatformAttributeResponse attribute : schema) {
            if (isSystemManaged(attribute)) continue;
            if (platform == PlatformType.TIKTOK && Boolean.TRUE.equals(attribute.getSaleProperty())) {
                String optionKey = tikTokVariantOptionKey(attribute);
                if (optionKey == null || variants.size() <= 1) continue;
                boolean attributeIsUsed = variants.stream().anyMatch(variant ->
                        !isEmpty(variant.getOptionValues() == null ? null : variant.getOptionValues().get(optionKey)));
                if (!attributeIsUsed) continue;
                for (ProductVariant variant : variants) {
                    Object value = variant.getOptionValues() == null
                            ? null : variant.getOptionValues().get(optionKey);
                    if (isEmpty(value)) {
                        return "Missing TikTok " + displayName(attribute)
                                + " value for SKU " + variant.getSku();
                    }
                }
                continue;
            }
            boolean requiredForListing = Boolean.TRUE.equals(attribute.getRequired())
                    || (platform == PlatformType.TIKTOK && isTikTokListingAttribute(attribute));
            if (!requiredForListing) continue;
            if (Boolean.TRUE.equals(attribute.getSaleProperty())) {
                Map<String, String> skuMappings = firstValue(
                        valueMappings, attribute.getId(), attribute.getName());
                if (variants.isEmpty()) {
                    return "Missing product SKU for " + platform.name() + " attribute: " + displayName(attribute);
                }
                for (ProductVariant variant : variants) {
                    if (skuMappings == null || isEmpty(skuMappings.get(variant.getSku()))) {
                        return "Missing " + platform.name() + " " + displayName(attribute)
                                + " value for SKU " + variant.getSku();
                    }
                }
            } else {
                Object value = firstValue(attributes, attribute.getId(), attribute.getName());
                if (isEmpty(value)) {
                    return "Missing required attribute: " + displayName(attribute);
                }
                if (platform == PlatformType.LAZADA
                        && isLazadaSizeChartAttribute(attribute)
                        && !isHttpUrl(String.valueOf(value))) {
                    return "Lazada size chart image URL must start with http:// or https://";
                }
            }
        }
        return null;
    }

    private Map<String, Map<String, String>> filterVariantValueMappings(
            Map<String, Map<String, String>> mappings,
            List<PlatformAttributeResponse> schema) {
        if (mappings == null || mappings.isEmpty()) return new HashMap<>();
        Map<String, Map<String, String>> result = new HashMap<>();
        for (PlatformAttributeResponse attribute : schema) {
            if (!Boolean.TRUE.equals(attribute.getSaleProperty())) continue;
            Map<String, String> requested = firstValue(mappings, attribute.getId(), attribute.getName());
            if (requested == null || requested.isEmpty()) continue;

            Map<String, String> normalized = new HashMap<>();
            requested.forEach((sku, selectedValue) -> {
                if (sku == null || sku.isBlank() || selectedValue == null || selectedValue.isBlank()) return;
                String platformValue = selectedValue;
                if (attribute.getOptions() != null && !attribute.getOptions().isEmpty()) {
                    PlatformAttributeOptionResponse option = attribute.getOptions().stream()
                            .filter(candidate -> selectedValue.equals(candidate.getId())
                                    || selectedValue.equals(candidate.getName())
                                    || selectedValue.equals(candidate.getPlatformValue()))
                            .findFirst()
                            .orElse(null);
                    platformValue = option == null ? null
                            : textOrDefault(option.getPlatformValue(), option.getName());
                }
                if (platformValue != null && !platformValue.isBlank()) normalized.put(sku, platformValue);
            });
            String attributeKey = textOrDefault(attribute.getName(), attribute.getId());
            if (!normalized.isEmpty() && attributeKey != null) result.put(attributeKey, normalized);
        }
        return result;
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
            if (!allowed.contains(key) || isEmpty(value)) return;
            PlatformAttributeResponse attribute = schema.stream()
                    .filter(item -> key.equals(item.getId()) || key.equals(item.getName()))
                    .findFirst()
                    .orElse(null);
            result.put(key, normalizeAttributeValue(value, attribute, platform));
        });
        return result;
    }

    private Object normalizeAttributeValue(Object value,
                                           PlatformAttributeResponse attribute,
                                           PlatformType platform) {
        if (platform != PlatformType.LAZADA || attribute == null
                || attribute.getOptions() == null || attribute.getOptions().isEmpty()) {
            return value;
        }
        String selectedValue = String.valueOf(value);
        PlatformAttributeOptionResponse selectedOption = attribute.getOptions().stream()
                .filter(option -> selectedValue.equals(option.getId())
                        || selectedValue.equals(option.getName())
                        || selectedValue.equals(option.getPlatformValue()))
                .findFirst()
                .orElse(null);
        return selectedOption == null
                ? value
                : textOrDefault(selectedOption.getPlatformValue(), selectedOption.getName());
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
                .listingTitle(stringValue(config.get("listingTitle")))
                .attributes(mapValue(config.get("attributes")))
                .variantAttributeValueMappings(nestedStringMapValue(config.get("variantAttributeValueMappings")))
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
        return metadataCodec().decode(channelProduct);
    }

    private void persistConfig(ChannelProduct channelProduct, Map<String, Object> config) {
        metadataCodec().persist(channelProduct, config);
    }

    private void persistConfigChange(ChannelProduct channelProduct,
                                     Map<String, Object> previousConfig,
                                     Map<String, Object> nextConfig) {
        metadataCodec().persistChange(channelProduct, previousConfig, nextConfig);
    }

    private void markNotReady(Map<String, Object> config, String error) {
        config.put("readyToSync", false);
        config.put("configurationError", error);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        return metadataCodec().mapValue(value);
    }

    private Map<String, Map<String, String>> nestedStringMapValue(Object value) {
        return metadataCodec().nestedStringMapValue(value);
    }

    private boolean isEmpty(Object value) {
        return configValidator().isEmpty(value);
    }

    private String shippingValidationError(ChannelProduct channelProduct) {
        return lazadaValidator().shippingValidationError(channelProduct);
    }

    private String platformProductValidationError(ChannelProduct channelProduct) {
        return tikTokValidator().productValidationError(channelProduct, config(channelProduct));
    }

    private TikTokProductTitleResult resolveTikTokTitle(Product product, Map<String, Object> config) {
        return tikTokValidator().resolveTitle(product, config);
    }

    private boolean isPositiveNumber(Object value) {
        return configValidator().isPositiveNumber(value);
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

    private String tikTokVariantOptionKey(PlatformAttributeResponse attribute) {
        if ("100000".equals(attribute.getId())) return "Màu";
        if ("100007".equals(attribute.getId())) return "Size";
        String name = attribute.getName() == null ? "" : attribute.getName().trim().toLowerCase(Locale.ROOT);
        if (name.contains("màu") || name.contains("color")) return "Màu";
        if (name.contains("kích cỡ") || name.equals("size")) return "Size";
        return null;
    }

    private boolean isLazadaSizeChartAttribute(PlatformAttributeResponse attribute) {
        if (attribute.getName() == null) return false;
        String normalized = attribute.getName().trim().toLowerCase(Locale.ROOT)
                .replace(' ', '_').replace('-', '_');
        return "size_chart".equals(normalized) || "size_chart_image".equals(normalized);
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

    private ChannelProductConfigMetadataCodec metadataCodec() {
        return new ChannelProductConfigMetadataCodec(objectMapper);
    }

    private ProductChannelConfigValidator configValidator() {
        return new ProductChannelConfigValidator();
    }

    private LazadaProductConfigValidator lazadaValidator() {
        return new LazadaProductConfigValidator();
    }

    private TikTokProductConfigValidator tikTokValidator() {
        return new TikTokProductConfigValidator(tikTokProductTitleResolver);
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
