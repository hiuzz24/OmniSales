package fu.osms.sync.tiktok.impl;

import fu.osms.catalog.entity.Category;
import fu.osms.catalog.entity.Product;
import fu.osms.catalog.enums.CategoryStatus;
import fu.osms.catalog.repository.CategoryRepository;
import fu.osms.catalog.repository.ProductRepository;
import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelProduct;
import fu.osms.channel.entity.ChannelProductVariant;
import fu.osms.channel.repository.ChannelProductRepository;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.common.enums.PlatformType;
import fu.osms.sync.tiktok.TikTokAuthorizedApiClient;
import fu.osms.sync.tiktok.TikTokProductDetailEnrichmentService;
import fu.osms.sync.service.PlatformCatalogOwnershipPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TikTokProductDetailEnrichmentServiceImpl implements TikTokProductDetailEnrichmentService {

    private final ChannelRepository channelRepository;
    private final ChannelProductRepository channelProductRepository;
    private final ChannelProductVariantRepository channelProductVariantRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final TikTokAuthorizedApiClient tikTokApiClient;
    private final TransactionTemplate transactionTemplate;
    private final PlatformCatalogOwnershipPolicy catalogOwnershipPolicy;

    @Override
    @Async("syncJobExecutor")
    public void enrichChannelProducts(UUID channelId, Collection<UUID> channelProductIds) {
        if (channelProductIds == null || channelProductIds.isEmpty()) {
            return;
        }
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

        List<ChannelProduct> mappings = channelProductRepository
                .findActiveByChannelIdAndIdInWithProduct(channelId, channelProductIds).stream()
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
        if (catalogOwnershipPolicy.isPlatformOwned(
                channelProduct, product, channelProduct.getExternalProductId())) {
            product.setName(firstNonBlank(productTitle(productNode), product.getName()));
            product.setDescription(firstNonBlank(
                    stringValue(productNode.get("description")), product.getDescription()));
            product.setBrand(firstNonBlank(
                    nestedText(productNode, "brand", "name"), product.getBrand()));
            try {
                Category resolvedCategory = resolveCategory(productNode);
                if (resolvedCategory != null) {
                    product.setCategory(resolvedCategory);
                }
            } catch (Exception e) {
                log.warn("[TikTokDetailEnrichment] Could not resolve category for channelProductId={}, error={}",
                        channelProductId, e.getMessage());
            }
            productRepository.save(product);
        }

        channelProduct.setExternalStatus(firstNonBlank(
                stringValue(productNode.get("status")),
                stringValue(productNode.get("product_status")),
                channelProduct.getExternalStatus()
        ));
        channelProductRepository.save(channelProduct);

        enrichVariantMappings(channelProduct, productNode);
    }

    private void enrichVariantMappings(ChannelProduct channelProduct, Map<String, Object> productNode) {
        Map<String, Map<String, Object>> skusById = indexById(listOfMaps(productNode.get("skus")));
        for (ChannelProductVariant mapping : channelProductVariantRepository.findByChannelProductId(channelProduct.getId())) {
            String externalVariantId = mapping.getExternalVariantId();
            Map<String, Object> skuNode = skusById.get(externalVariantId);
            if (skuNode == null || mapping.getVariant() == null) {
                continue;
            }

            BigDecimal price = skuPrice(skuNode);
            if (price != null) {
                mapping.setExternalPrice(price);
            }

            mapping.setExternalSku(firstNonBlank(stringValue(skuNode.get("seller_sku")), mapping.getExternalSku()));
            channelProductVariantRepository.save(mapping);
        }
    }

    private Map<String, Object> extractProductNode(Map<String, Object> response) {
        Map<String, Object> data = map(response.get("data"));
        Map<String, Object> product = map(data.get("product"));
        return product.isEmpty() ? data : product;
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

    private BigDecimal skuPrice(Map<String, Object> skuNode) {
        Map<String, Object> price = map(skuNode.get("price"));
        return moneyAmount(firstNonBlank(
                stringValue(price.get("tax_exclusive_price")),
                stringValue(price.get("sale_price")),
                stringValue(price.get("amount"))
        ));
    }

    private String productTitle(Map<String, Object> node) {
        return firstNonBlank(
                stringValue(node.get("title")),
                stringValue(node.get("name")),
                stringValue(node.get("product_name"))
        );
    }

    private Category resolveCategory(Map<String, Object> productNode) {
        String externalCategoryId = firstNonBlank(
                stringValue(productNode.get("category_id")),
                stringValue(productNode.get("categoryId"))
        );
        String categoryName = firstNonBlank(
                stringValue(productNode.get("category_name")),
                stringValue(productNode.get("categoryName"))
        );
        if (!hasText(externalCategoryId) || !hasText(categoryName)) {
            for (Map<String, Object> categoryNode : listOfMaps(productNode.get("category_list"))) {
                String id = firstNonBlank(
                        stringValue(categoryNode.get("id")),
                        stringValue(categoryNode.get("category_id"))
                );
                String name = firstNonBlank(
                        stringValue(categoryNode.get("name")),
                        stringValue(categoryNode.get("category_name"))
                );
                if (!hasText(id) || !hasText(name)) {
                    continue;
                }
                externalCategoryId = id;
                categoryName = name;
            }
        }
        if (!hasText(externalCategoryId) && !hasText(categoryName)) {
            return null;
        }

        String slug = hasText(externalCategoryId)
                ? "tiktok-" + slugify(externalCategoryId)
                : "tiktok-" + slugify(categoryName);
        String resolvedName = firstNonBlank(categoryName, "TikTok " + externalCategoryId);

        Category category = categoryRepository.findBySlug(slug)
                .or(() -> categoryRepository.findFirstByNameIgnoreCase(resolvedName))
                .orElseGet(Category::new);
        category.setName(resolvedName);
        category.setSlug(slug);
        if (category.getSortOrder() == null) {
            category.setSortOrder(0);
        }
        if (category.getStatus() == null) {
            category.setStatus(CategoryStatus.ACTIVE);
        }
        return categoryRepository.save(category);
    }

    private String slugify(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase();
        normalized = normalized.replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return normalized.isBlank() ? "unknown" : normalized;
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

    private boolean hasText(String value) {
        return value != null && !value.isBlank() && !"null".equalsIgnoreCase(value);
    }

}
