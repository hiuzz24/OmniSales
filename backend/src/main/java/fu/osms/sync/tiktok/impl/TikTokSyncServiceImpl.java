package fu.osms.sync.tiktok.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.catalog.dto.response.PlatformAttributeResponse;
import fu.osms.catalog.dto.TikTokProductTitleInput;
import fu.osms.catalog.dto.TikTokProductTitleResult;
import fu.osms.catalog.entity.Product;
import fu.osms.catalog.entity.ProductImage;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.catalog.service.PlatformLookupService;
import fu.osms.catalog.service.ProductChannelConfigService;
import fu.osms.catalog.service.TikTokProductTitleResolver;
import fu.osms.catalog.service.impl.PlatformLookupServiceFactory;
import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelProduct;
import fu.osms.channel.entity.ChannelProductVariant;
import fu.osms.channel.repository.ChannelProductRepository;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.enums.SyncStatus;
import fu.osms.sync.service.PlatformSyncService;
import fu.osms.sync.tiktok.TikTokAuthorizedApiClient;
import fu.osms.sync.tiktok.TikTokProductPayloadBuilder;
import fu.osms.sync.tiktok.dto.TikTokProductPayloadContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TikTokSyncServiceImpl implements PlatformSyncService {

    private static final Set<String> LISTING_REQUIRED_ATTRIBUTE_IDS = Set.of("100149", "101489", "101490");
    private static final String CONFIG_KEY = "platformConfig";
    private static final String IMAGE_CACHE_KEY = "tiktokImageUris";
    private static final String SIZE_CHART_IMAGE_CACHE_KEY = "tiktokSizeChartImageUris";
    private static final String MANAGED_KEY = "tiktokManagedByOsms";

    private final TikTokAuthorizedApiClient tikTokApiClient;
    private final ChannelProductRepository channelProductRepository;
    private final ChannelProductVariantRepository channelProductVariantRepository;
    private final ProductChannelConfigService productChannelConfigService;
    private final PlatformLookupServiceFactory lookupServiceFactory;
    private final TikTokProductPayloadBuilder payloadBuilder;
    private final TikTokProductTitleResolver titleResolver;
    private final ObjectMapper objectMapper;

    @Override
    public boolean syncProduct(Product product,
                               List<ProductVariant> variants,
                               List<ProductImage> images,
                               Channel channel,
                               ChannelProduct channelProduct) {
        try {
            Map<String, Object> config = productConfig(channelProduct);
            validate(product, variants, images, channel, channelProduct, config);

            boolean isCreate = channelProduct.getExternalProductId() == null || channelProduct.getExternalProductId().isBlank();
            List<String> imageUris = resolveImageUris(channel.getId(), channelProduct, images);
            String sizeChartImageUri = resolveSizeChartImageUri(channel.getId(), channelProduct, config);
            List<PlatformAttributeResponse> schema = lookup(channel).getAttributes(
                    channel.getId(), text(config.get("categoryId")), text(config.get("categoryVersion")));
            validateRequiredProductAttributes(schema, config);
            Map<UUID, String> externalVariantIdByVariantId = channelProductVariantRepository
                    .findByChannelProductId(channelProduct.getId()).stream()
                    .filter(value -> value.getExternalVariantId() != null
                            && !value.getExternalVariantId().isBlank())
                    .collect(Collectors.toMap(value -> value.getVariant().getId(), ChannelProductVariant::getExternalVariantId));
            boolean hasNewSku = variants.stream()
                    .filter(variant -> !Boolean.FALSE.equals(variant.getIsActive()))
                    .anyMatch(variant -> !externalVariantIdByVariantId.containsKey(variant.getId()));
            String defaultWarehouseId = isCreate || hasNewSku
                    ? resolveDefaultWarehouseId(channel)
                    : null;
            String rawPayload = objectMapper.writeValueAsString(payloadBuilder.buildPayload(
                    new TikTokProductPayloadContext(
                            product,
                            variants,
                            config,
                            schema,
                            imageUris,
                            sizeChartImageUri,
                            externalVariantIdByVariantId,
                            isCreate,
                            defaultWarehouseId,
                            currency(channel)
                    )
            ));
            Map<String, String> query = Map.of("shop_cipher", shopCipher(channel));
            String response = isCreate
                    ? tikTokApiClient.executePost(channel.getId(), "/product/202309/products", query, rawPayload)
                    : tikTokApiClient.executePut(channel.getId(), "/product/202309/products/" + channelProduct.getExternalProductId(), query, rawPayload);

            JsonNode root = objectMapper.readTree(response);
            ensureSuccess(root);
            mapResponse(root.path("data"), variants, channelProduct, isCreate);
            channelProduct.setSyncStatus(SyncStatus.SYNCED);
            channelProduct.setLastSyncedAt(OffsetDateTime.now());
            channelProduct.setLastSyncError(null);
            channelProductRepository.save(channelProduct);
            return true;
        } catch (Exception e) {
            log.error("[TikTokSync] Failed productId={} channelId={}: {}", product.getId(), channel.getId(), e.getMessage(), e);
            channelProduct.setSyncStatus(SyncStatus.FAILED);
            channelProduct.setLastSyncError(e.getMessage());
            channelProductRepository.save(channelProduct);
            return false;
        }
    }

    private void validate(Product product,
                          List<ProductVariant> variants,
                          List<ProductImage> images,
                          Channel channel,
                          ChannelProduct mapping,
                          Map<String, Object> config) {
        if (!productChannelConfigService.isReady(mapping)) {
            throw new IllegalStateException(defaultMessage(productChannelConfigService.configurationError(mapping),
                    "Missing TikTok product configuration"));
        }
        if (product.getDescription() == null || product.getDescription().isBlank()) {
            throw new IllegalStateException("TikTok product description is required");
        }
        TikTokProductTitleResult titleResult = titleResolver.resolve(new TikTokProductTitleInput(
                text(config.get("listingTitle")),
                product.getName(),
                text(config.get("categoryName")),
                text(config.get("brandName")),
                product.getDescription()
        ));
        if (!titleResult.valid()) throw new IllegalStateException(titleResult.validationError());
        if (product.getWeightGrams() == null || product.getWeightGrams() <= 0) {
            throw new IllegalStateException("TikTok package weight is required");
        }
        if (images == null || images.stream().noneMatch(image -> image.getVariant() == null
                && image.getUrl() != null && !image.getUrl().isBlank())) {
            throw new IllegalStateException("TikTok product requires at least one image");
        }
        if (text(config.get("sizeChartImageUrl")) == null) {
            throw new IllegalStateException("TikTok size chart image is required");
        }
        if (variants == null || variants.isEmpty()) {
            throw new IllegalStateException("TikTok product requires at least one SKU");
        }
        for (ProductVariant variant : variants) {
            if (Boolean.FALSE.equals(variant.getIsActive())) continue;
            if (variant.getSku() == null || variant.getSku().isBlank()) {
                throw new IllegalStateException("TikTok seller SKU is required");
            }
            if (variant.getPrice() == null || variant.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalStateException("Missing selling price for SKU " + variant.getSku());
            }
        }
        if (mapping.getExternalProductId() != null && !mapping.getExternalProductId().isBlank()) {
            if (!Boolean.TRUE.equals(mapping.getMetadata() == null ? null : mapping.getMetadata().get(MANAGED_KEY))) {
                throw new IllegalStateException("TikTok update is only supported for products created by OSMS");
            }
        }
        if (config.get("categoryId") == null) {
            throw new IllegalStateException("TikTok category is required");
        }
        if (channel.getMetadata() == null || text(channel.getMetadata().get("shopCipher")) == null) {
            throw new IllegalStateException("TikTok shop cipher is missing. Reconnect the channel.");
        }
        validateGrantedScopes(channel);
    }

    private List<String> resolveImageUris(UUID channelId,
                                          ChannelProduct channelProduct,
                                          List<ProductImage> images) throws Exception {
        Map<String, Object> metadata = channelProduct.getMetadata() == null ? new HashMap<>() : new HashMap<>(channelProduct.getMetadata());
        Map<String, String> cache = stringMap(metadata.get(IMAGE_CACHE_KEY));
        List<String> uris = new ArrayList<>();
        for (ProductImage image : images) {
            if (image.getVariant() != null) continue;
            if (image.getUrl() == null || image.getUrl().isBlank()) continue;
            String uri = cache.get(image.getUrl());
            if (uri == null) {
                JsonNode root = objectMapper.readTree(
                        tikTokApiClient.uploadProductImage(channelId, image.getUrl(), "MAIN_IMAGE"));
                ensureSuccess(root);
                uri = root.path("data").path("uri").asText(null);
                if (uri == null || uri.isBlank()) throw new IllegalStateException("TikTok image upload response is missing uri");
                cache.put(image.getUrl(), uri);
            }
            uris.add(uri);
        }
        metadata.put(IMAGE_CACHE_KEY, cache);
        channelProduct.setMetadata(metadata);
        return uris;
    }

    private String resolveSizeChartImageUri(UUID channelId,
                                            ChannelProduct channelProduct,
                                            Map<String, Object> config) throws Exception {
        String imageUrl = text(config.get("sizeChartImageUrl"));
        if (imageUrl == null) {
            throw new IllegalStateException("TikTok size chart image is required");
        }
        Map<String, Object> metadata = channelProduct.getMetadata() == null
                ? new HashMap<>() : new HashMap<>(channelProduct.getMetadata());
        Map<String, String> cache = stringMap(metadata.get(SIZE_CHART_IMAGE_CACHE_KEY));
        String uri = cache.get(imageUrl);
        if (uri == null) {
            JsonNode root = objectMapper.readTree(
                    tikTokApiClient.uploadProductImage(channelId, imageUrl, "SIZE_CHART_IMAGE"));
            ensureSuccess(root);
            uri = root.path("data").path("uri").asText(null);
            if (uri == null || uri.isBlank()) {
                throw new IllegalStateException("TikTok size chart upload response is missing uri");
            }
            cache.put(imageUrl, uri);
        }
        metadata.put(SIZE_CHART_IMAGE_CACHE_KEY, cache);
        channelProduct.setMetadata(metadata);
        return uri;
    }

    private void mapResponse(JsonNode data, List<ProductVariant> variants, ChannelProduct channelProduct, boolean isCreate) {
        String productId = firstText(data, "id", "product_id");
        if (isCreate && (productId == null || productId.isBlank())) {
            throw new IllegalStateException("TikTok create response is missing product ID");
        }
        if (productId != null && !productId.isBlank()) channelProduct.setExternalProductId(productId);
        Map<String, ProductVariant> variantsBySku = variants.stream()
                .collect(Collectors.toMap(ProductVariant::getSku, value -> value, (first, ignored) -> first));
        JsonNode skus = data.path("skus");
        if (skus.isArray()) {
            for (JsonNode sku : skus) {
                String sellerSku = firstText(sku, "seller_sku");
                String externalSkuId = firstText(sku, "id", "sku_id");
                ProductVariant local = variantsBySku.get(sellerSku);
                if (local == null || externalSkuId == null || externalSkuId.isBlank()) continue;
                ChannelProductVariant mapping = channelProductVariantRepository
                        .findByChannelProductIdAndVariantId(channelProduct.getId(), local.getId())
                        .orElse(ChannelProductVariant.builder().channelProduct(channelProduct).variant(local)
                                .externalVariantId(externalSkuId).build());
                mapping.setExternalVariantId(externalSkuId);
                mapping.setExternalSku(sellerSku);
                String price = firstText(sku, "sale_price", "price");
                if (price != null) mapping.setExternalPrice(new BigDecimal(price));
                mapping.setSyncStatus(SyncStatus.SYNCED);
                mapping.setLastSyncedAt(OffsetDateTime.now());
                channelProductVariantRepository.save(mapping);
            }
        }
        if (isCreate) {
            Map<String, Object> metadata = channelProduct.getMetadata() == null ? new HashMap<>() : new HashMap<>(channelProduct.getMetadata());
            metadata.put(MANAGED_KEY, true);
            channelProduct.setMetadata(metadata);
        }
    }

    private PlatformLookupService lookup(Channel channel) {
        return lookupServiceFactory.get(channel.getPlatform());
    }

    private String shopCipher(Channel channel) {
        return text(channel.getMetadata() == null ? null : channel.getMetadata().get("shopCipher"));
    }

    private String currency(Channel channel) {
        String region = text(channel.getMetadata() == null ? null : channel.getMetadata().get("region"));
        if (region == null) {
            region = text(channel.getMetadata() == null ? null : channel.getMetadata().get("sellerBaseRegion"));
        }
        return switch (region == null ? "VN" : region.toUpperCase()) {
            case "ID" -> "IDR";
            case "MY" -> "MYR";
            case "PH" -> "PHP";
            case "SG" -> "SGD";
            case "TH" -> "THB";
            default -> "VND";
        };
    }

    private String resolveDefaultWarehouseId(Channel channel) throws Exception {
        String response = tikTokApiClient.executeGet(
                channel.getId(),
                "/logistics/202309/warehouses",
                Map.of("shop_cipher", shopCipher(channel))
        );
        JsonNode root = objectMapper.readTree(response);
        ensureSuccess(root);
        JsonNode warehouses = root.path("data").path("warehouses");
        if (!warehouses.isArray()) {
            throw new IllegalStateException("TikTok did not return any warehouses");
        }
        List<JsonNode> enabled = new ArrayList<>();
        for (JsonNode warehouse : warehouses) {
            if ("ENABLED".equalsIgnoreCase(warehouse.path("effect_status").asText())) {
                enabled.add(warehouse);
                if (warehouse.path("is_default").asBoolean(false)) {
                    String id = warehouse.path("id").asText(null);
                    if (id != null && !id.isBlank()) return id;
                }
            }
        }
        if (enabled.size() == 1) {
            String id = enabled.get(0).path("id").asText(null);
            if (id != null && !id.isBlank()) return id;
        }
        throw new IllegalStateException("TikTok requires one enabled default warehouse before creating products");
    }

    private void validateGrantedScopes(Channel channel) {
        Object configured = channel.getMetadata() == null ? null : channel.getMetadata().get("grantedScopes");
        if (!(configured instanceof Collection<?> scopes) || scopes.isEmpty()) {
            return;
        }
        Set<String> values = scopes.stream().map(String::valueOf).collect(Collectors.toSet());
        if (!values.contains("seller.product.basic") || !values.contains("seller.product.write")) {
            throw new IllegalStateException("TikTok authorization is missing seller.product.basic or seller.product.write");
        }
    }

    private void validateRequiredProductAttributes(List<PlatformAttributeResponse> schema,
                                                   Map<String, Object> config) {
        Map<String, Object> attributes = map(config.get("attributes"));
        for (PlatformAttributeResponse attribute : schema) {
            boolean requiredForListing = Boolean.TRUE.equals(attribute.getRequired())
                    || LISTING_REQUIRED_ATTRIBUTE_IDS.contains(attribute.getId());
            if (!requiredForListing
                    || Boolean.TRUE.equals(attribute.getSaleProperty())) {
                continue;
            }
            Object selected = attributes.get(attribute.getId());
            if (selected == null && attribute.getName() != null) {
                selected = attributes.get(attribute.getName());
            }
            if (selected == null || String.valueOf(selected).isBlank()) {
                throw new IllegalStateException("Missing required TikTok attribute: "
                        + defaultMessage(attribute.getLabel(), attribute.getName()));
            }
            Map<String, Object> selectedMap = map(selected);
            if (!selectedMap.isEmpty()
                    && text(selectedMap.get("valueId")) == null
                    && text(selectedMap.get("valueName")) == null) {
                throw new IllegalStateException("Missing required TikTok attribute: "
                        + defaultMessage(attribute.getLabel(), attribute.getName()));
            }
        }
    }

    private Map<String, Object> productConfig(ChannelProduct mapping) {
        return map(mapping.getMetadata() == null ? null : mapping.getMetadata().get(CONFIG_KEY));
    }

    private void ensureSuccess(JsonNode root) {
        if (root.path("code").asInt(-1) != 0) {
            throw new IllegalStateException("TikTok API error: " + root.path("message").asText("Unknown error"));
        }
    }

    private String firstText(JsonNode node, String... names) {
        for (String name : names) {
            String value = node.path(name).asText(null);
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> map ? objectMapper.convertValue(map, new TypeReference<>() {}) : new HashMap<>();
    }

    private Map<String, String> stringMap(Object value) {
        Map<String, String> result = new HashMap<>();
        map(value).forEach((key, entry) -> {
            if (entry != null) {
                result.put(key, String.valueOf(entry));
            }
        });
        return result;
    }

    private String text(Object value) {
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value);
    }

    private String defaultMessage(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
