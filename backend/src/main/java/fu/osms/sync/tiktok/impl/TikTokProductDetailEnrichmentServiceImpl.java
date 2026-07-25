package fu.osms.sync.tiktok.impl;

import fu.osms.catalog.entity.Category;
import fu.osms.catalog.entity.Product;
import fu.osms.catalog.entity.ProductImage;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.catalog.enums.CategoryStatus;
import fu.osms.catalog.enums.ProductStatus;
import fu.osms.catalog.repository.CategoryRepository;
import fu.osms.catalog.repository.ProductImageRepository;
import fu.osms.catalog.repository.ProductRepository;
import fu.osms.catalog.repository.ProductVariantRepository;
import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelProduct;
import fu.osms.channel.entity.ChannelProductVariant;
import fu.osms.channel.repository.ChannelProductRepository;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.enums.SyncStatus;
import fu.osms.sync.tiktok.TikTokAuthorizedApiClient;
import fu.osms.sync.tiktok.TikTokProductDetailEnrichmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TikTokProductDetailEnrichmentServiceImpl implements TikTokProductDetailEnrichmentService {

    private final ChannelRepository channelRepository;
    private final ChannelProductRepository channelProductRepository;
    private final ChannelProductVariantRepository channelProductVariantRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductImageRepository productImageRepository;
    private final CategoryRepository categoryRepository;
    private final TikTokAuthorizedApiClient tikTokApiClient;
    private final TransactionTemplate transactionTemplate;

    @Override
    @Async("syncJobExecutor")
    public void enrichChannelProducts(UUID channelId) {
        Channel channel = channelRepository.findById(channelId)
                .filter(item -> item.getDeletedAt() == null && item.getPlatform() == PlatformType.TIKTOK)
                .orElse(null);
        if (channel == null) {
            log.warn("[TikTokDetailEnrichment] Skip enrichment because channel is missing or not TikTok channelId={}", channelId);
            return;
        }

        String shopCipher = shopCipher(channel);
        if (!hasText(shopCipher)) {
            log.warn("[TikTokDetailEnrichment] Skip enrichment because shopCipher is missing channelId={}", channelId);
            return;
        }

        List<ChannelProduct> mappings = channelProductRepository.findActiveByChannelIdWithProduct(channelId).stream()
                .filter(mapping -> hasText(mapping.getExternalProductId()))
                .toList();
        log.info("[TikTokDetailEnrichment] Starting product detail enrichment channelId={}, productCount={}",
                channelId, mappings.size());

        int successCount = 0;
        int failedCount = 0;
        for (ChannelProduct mapping : mappings) {
            try {
                Map<String, Object> response = tikTokApiClient.getProduct(
                        channelId,
                        shopCipher,
                        mapping.getExternalProductId()
                );
                Map<String, Object> productNode = extractProductNode(response);
                if (productNode.isEmpty()) {
                    log.warn("[TikTokDetailEnrichment] Empty product detail channelId={}, externalProductId={}",
                            channelId, mapping.getExternalProductId());
                    continue;
                }
                transactionTemplate.executeWithoutResult(status -> enrichProduct(mapping.getId(), productNode));
                successCount++;
            } catch (Exception e) {
                failedCount++;
                log.warn("[TikTokDetailEnrichment] Failed product detail enrichment channelId={}, externalProductId={}, error={}",
                        channelId, mapping.getExternalProductId(), e.getMessage(), e);
            }
        }

        log.info("[TikTokDetailEnrichment] Completed product detail enrichment channelId={}, success={}, failed={}",
                channelId, successCount, failedCount);
    }

    private void enrichProduct(UUID channelProductId, Map<String, Object> productNode) {
        ChannelProduct channelProduct = channelProductRepository.findById(channelProductId).orElse(null);
        if (channelProduct == null || channelProduct.getProduct() == null) {
            return;
        }

        Product product = channelProduct.getProduct();
        product.setName(firstNonBlank(productTitle(productNode), product.getName()));
        product.setDescription(firstNonBlank(stringValue(productNode.get("description")), product.getDescription()));
        product.setBrand(firstNonBlank(nestedText(productNode, "brand", "name"), product.getBrand()));
        String productStatus = firstNonBlank(
                stringValue(productNode.get("status")),
                stringValue(productNode.get("product_status"))
        );
        if (hasText(productStatus)) {
            product.setStatus(resolveStatus(productStatus));
        }
        product.setWeightGrams(firstNonNull(weightGrams(map(productNode.get("package_weight"))), product.getWeightGrams()));

        Category leafCategory = resolveCategoryChain(listOfMaps(productNode.get("category_chains")));
        if (leafCategory != null) {
            product.setCategory(leafCategory);
        }

        Map<String, Object> attributes = mutableMap(product.getAttributes());
        putIfPresent(attributes, "tiktokProductId", stringValue(productNode.get("id")));
        putIfPresent(attributes, "tiktokExternalProductId", stringValue(productNode.get("external_product_id")));
        putIfPresent(attributes, "tiktokProductAttributes", productNode.get("product_attributes"));
        putIfPresent(attributes, "tiktokPackageDimensions", productNode.get("package_dimensions"));
        putIfPresent(attributes, "tiktokPackageWeight", productNode.get("package_weight"));
        putIfPresent(attributes, "tiktokSizeChartImageUrl", imageUrl(map(map(productNode.get("size_chart")).get("image"))));
        putIfPresent(attributes, "tiktokCategoryChains", productNode.get("category_chains"));
        product.setAttributes(attributes);
        product = productRepository.save(product);

        channelProduct.setExternalStatus(firstNonBlank(
                stringValue(productNode.get("status")),
                stringValue(productNode.get("product_status")),
                channelProduct.getExternalStatus()
        ));
        Map<String, Object> channelMetadata = mutableMap(channelProduct.getMetadata());
        putIfPresent(channelMetadata, "tiktokAudit", productNode.get("audit"));
        putIfPresent(channelMetadata, "tiktokMainImageUris", mainImageUris(productNode));
        channelProduct.setMetadata(channelMetadata);
        channelProductRepository.save(channelProduct);

        upsertProductImages(product, extractMainImageUrls(productNode));
        enrichVariants(channelProduct, productNode);
    }

    private void enrichVariants(ChannelProduct channelProduct, Map<String, Object> productNode) {
        Map<String, Map<String, Object>> skusById = indexById(listOfMaps(productNode.get("skus")));
        for (ChannelProductVariant mapping : channelProductVariantRepository.findByChannelProductId(channelProduct.getId())) {
            String externalVariantId = mapping.getExternalVariantId();
            Map<String, Object> skuNode = skusById.get(externalVariantId);
            if (skuNode == null || mapping.getVariant() == null) {
                continue;
            }

            ProductVariant variant = mapping.getVariant();
            variant.setName(firstNonBlank(variantName(skuNode), variant.getName()));
            if (mapping.getSyncStatus() != SyncStatus.OUT_OF_SYNC) {
                BigDecimal price = skuPrice(skuNode);
                if (price != null) {
                    variant.setPrice(price);
                    mapping.setExternalPrice(price);
                }
            }
            variant.setWeightGrams(firstNonNull(weightGrams(map(skuNode.get("sku_weight"))), variant.getWeightGrams()));
            Map<String, Object> optionValues = salesAttributes(skuNode);
            if (!optionValues.isEmpty()) {
                variant.setOptionValues(optionValues);
            }
            productVariantRepository.save(variant);

            mapping.setExternalSku(firstNonBlank(stringValue(skuNode.get("seller_sku")), mapping.getExternalSku()));
            Map<String, Object> metadata = mutableMap(mapping.getMetadata());
            putIfPresent(metadata, "tiktokExternalSkuId", skuNode.get("external_sku_id"));
            putIfPresent(metadata, "tiktokSkuDimensions", skuNode.get("sku_dimensions"));
            putIfPresent(metadata, "tiktokSkuWeight", skuNode.get("sku_weight"));
            putIfPresent(metadata, "tiktokStatusInfo", skuNode.get("status_info"));
            mapping.setMetadata(metadata);
            channelProductVariantRepository.save(mapping);
        }
    }

    private void upsertProductImages(Product product, List<String> urls) {
        if (urls.isEmpty()) {
            return;
        }
        productImageRepository.deleteProductLevelImages(product.getId());
        List<ProductImage> images = new ArrayList<>();
        for (int index = 0; index < urls.size(); index++) {
            images.add(ProductImage.builder()
                    .product(product)
                    .variant(null)
                    .url(urls.get(index))
                    .sortOrder((short) index)
                    .isPrimary(index == 0)
                    .build());
        }
        productImageRepository.saveAll(images);
    }

    private Category resolveCategoryChain(List<Map<String, Object>> categoryNodes) {
        Category parent = null;
        for (Map<String, Object> node : categoryNodes) {
            String externalId = stringValue(node.get("id"));
            String name = firstNonBlank(
                    stringValue(node.get("local_name")),
                    stringValue(node.get("name")),
                    externalId
            );
            if (!hasText(name)) {
                continue;
            }
            String slug = "tiktok-" + firstNonBlank(externalId, toSlug(name));
            Category category = categoryRepository.findBySlug(slug).orElseGet(Category::new);
            category.setName(name);
            category.setSlug(slug);
            category.setParent(parent);
            category.setSortOrder(category.getSortOrder() == null ? 0 : category.getSortOrder());
            category.setStatus(category.getStatus() == null ? CategoryStatus.ACTIVE : category.getStatus());
            parent = categoryRepository.save(category);
        }
        return parent;
    }

    private Map<String, Object> extractProductNode(Map<String, Object> response) {
        Map<String, Object> data = map(response.get("data"));
        Map<String, Object> product = map(data.get("product"));
        return product.isEmpty() ? data : product;
    }

    private List<String> extractMainImageUrls(Map<String, Object> productNode) {
        List<String> urls = new ArrayList<>();
        for (Map<String, Object> imageNode : listOfMaps(productNode.get("main_images"))) {
            String url = imageUrl(imageNode);
            if (hasText(url) && !urls.contains(url)) {
                urls.add(url);
            }
        }
        return urls;
    }

    private List<String> mainImageUris(Map<String, Object> productNode) {
        return listOfMaps(productNode.get("main_images")).stream()
                .map(image -> stringValue(image.get("uri")))
                .filter(this::hasText)
                .toList();
    }

    private String imageUrl(Map<String, Object> imageNode) {
        return firstNonBlank(
                stringValue(imageNode.get("url")),
                firstString(imageNode.get("urls")),
                firstString(imageNode.get("thumb_urls"))
        );
    }

    private Map<String, Map<String, Object>> indexById(List<Map<String, Object>> nodes) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map<String, Object> node : nodes) {
            String id = stringValue(node.get("id"));
            if (hasText(id)) {
                result.put(id, node);
            }
        }
        return result;
    }

    private Map<String, Object> salesAttributes(Map<String, Object> skuNode) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map<String, Object> attribute : listOfMaps(skuNode.get("sales_attributes"))) {
            String name = firstNonBlank(stringValue(attribute.get("name")), stringValue(attribute.get("id")));
            String value = firstNonBlank(stringValue(attribute.get("value_name")), stringValue(attribute.get("value_id")));
            if (hasText(name) && hasText(value)) {
                result.put(name, value);
            }
        }
        return result;
    }

    private String variantName(Map<String, Object> skuNode) {
        String joinedAttributes = String.join(" / ", salesAttributes(skuNode).values().stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .filter(this::hasText)
                .toList());
        return firstNonBlank(joinedAttributes, stringValue(skuNode.get("seller_sku")));
    }

    private BigDecimal skuPrice(Map<String, Object> skuNode) {
        Map<String, Object> price = map(skuNode.get("price"));
        return moneyAmount(firstNonBlank(
                stringValue(price.get("tax_exclusive_price")),
                stringValue(price.get("sale_price")),
                stringValue(price.get("amount"))
        ));
    }

    private Integer weightGrams(Map<String, Object> weightNode) {
        BigDecimal value = moneyAmount(weightNode.get("value"));
        if (value == null) {
            return null;
        }
        String unit = stringValue(weightNode.get("unit"));
        BigDecimal grams = "KILOGRAM".equalsIgnoreCase(unit) || "KG".equalsIgnoreCase(unit)
                ? value.multiply(BigDecimal.valueOf(1000))
                : value;
        return grams.max(BigDecimal.ZERO).intValue();
    }

    private ProductStatus resolveStatus(String status) {
        return "ACTIVATE".equalsIgnoreCase(status) || "ACTIVE".equalsIgnoreCase(status)
                ? ProductStatus.ACTIVE
                : ProductStatus.INACTIVE;
    }

    private String productTitle(Map<String, Object> node) {
        return firstNonBlank(
                stringValue(node.get("title")),
                stringValue(node.get("name")),
                stringValue(node.get("product_name"))
        );
    }

    private String shopCipher(Channel channel) {
        Map<String, Object> metadata = mutableMap(channel.getMetadata());
        return firstNonBlank(
                stringValue(metadata.get("shopCipher")),
                stringValue(metadata.get("shop_cipher")),
                stringValue(metadata.get("cipher"))
        );
    }

    private BigDecimal moneyAmount(Object value) {
        if (value == null) {
            return null;
        }
        try {
            String normalized = value.toString().replaceAll("[^0-9.-]", "");
            return normalized.isBlank() ? null : new BigDecimal(normalized);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String nestedText(Map<String, Object> source, String key, String nestedKey) {
        return stringValue(map(source.get(key)).get(nestedKey));
    }

    private String firstString(Object value) {
        if (value instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            return first == null ? null : first.toString();
        }
        return null;
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> source
                ? (Map<String, Object>) source
                : Map.of();
    }

    private Map<String, Object> mutableMap(Map<String, Object> value) {
        return value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listOfMaps(Object value) {
        return value instanceof List<?> list
                ? list.stream().filter(Map.class::isInstance).map(item -> (Map<String, Object>) item).toList()
                : List.of();
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private <T> T firstNonNull(T first, T second) {
        return first != null ? first : second;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank() && !"null".equalsIgnoreCase(value);
    }

    private String toSlug(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return normalized.isBlank() ? UUID.randomUUID().toString() : normalized;
    }
}
