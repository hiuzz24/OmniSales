package fu.osms.sync.lazada.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.catalog.entity.Category;
import fu.osms.catalog.entity.Product;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.catalog.enums.CategoryStatus;
import fu.osms.catalog.enums.ProductStatus;
import fu.osms.catalog.repository.CategoryRepository;
import fu.osms.catalog.repository.ProductRepository;
import fu.osms.catalog.repository.ProductVariantRepository;
import fu.osms.channel.dto.response.ChannelImportSyncResponse;
import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelCredential;
import fu.osms.channel.entity.ChannelProduct;
import fu.osms.channel.entity.ChannelProductVariant;
import fu.osms.channel.repository.ChannelCredentialRepository;
import fu.osms.channel.repository.ChannelProductRepository;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.enums.SyncStatus;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.inventory.entity.Warehouse;
import fu.osms.inventory.entity.InventoryItem;
import fu.osms.inventory.entity.InventoryTransaction;
import fu.osms.inventory.enums.InvTxnType;
import fu.osms.inventory.repository.InventoryItemRepository;
import fu.osms.inventory.repository.InventoryTransactionRepository;
import fu.osms.inventory.repository.WarehouseRepository;
import fu.osms.sync.entity.SyncLog;
import fu.osms.sync.lazada.service.LazadaAuthorizedApiClient;
import fu.osms.channel.token.service.ChannelTokenService;
import fu.osms.sync.lazada.service.LazadaImportSyncService;
import fu.osms.sync.repository.SyncLogRepository;
import fu.osms.sync.service.MarketplaceInventoryPropagationService;
import fu.osms.sync.service.MarketplaceWarehouseConsistencyService;
import fu.osms.sync.service.SyncAlertService;
import fu.osms.sync.service.impl.ChannelProductAggregationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LazadaImportSyncServiceImpl implements LazadaImportSyncService {

    private static final int PRODUCT_PAGE_SIZE = 50;
    private static final int MAX_VARIANT_SKU_LENGTH = 100;
    private static final String WAREHOUSE_CODE_MARKER = "LAZADA_WAREHOUSE_CODE=";

    private final LazadaAuthorizedApiClient lazadaApiClient;
    private final ChannelTokenService channelTokenService;
    private final ObjectMapper objectMapper;
    private final ChannelRepository channelRepository;
    private final ChannelCredentialRepository credentialRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final CategoryRepository categoryRepository;
    private final ChannelProductRepository channelProductRepository;
    private final ChannelProductVariantRepository channelProductVariantRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final WarehouseRepository warehouseRepository;
    private final SyncLogRepository syncLogRepository;
    private final SyncAlertService syncAlertService;
    private final MarketplaceInventoryPropagationService marketplaceInventoryPropagationService;
    private final MarketplaceWarehouseConsistencyService marketplaceWarehouseConsistencyService;
    private final ChannelProductAggregationService channelProductAggregationService;

    @Override
    @Transactional
    public ChannelImportSyncResponse syncProductsAndWarehouses(UUID channelId) {
        Channel channel = channelRepository.findById(channelId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.CHANNEL_NOT_FOUND));

        if (channel.getPlatform() != PlatformType.LAZADA) {
            throw new IllegalArgumentException("Chỉ hỗ trợ đồng bộ kéo dữ liệu cho kênh Lazada.");
        }

        ChannelCredential credential = credentialRepository.findByChannelIdAndConnectionState(channelId, "CONNECTED")
                .orElseThrow(() -> new IllegalStateException("Kênh Lazada chưa có token kết nối."));
        if (credential.getAccessToken() == null || credential.getAccessToken().isBlank()) {
            throw new IllegalStateException("Kênh Lazada chưa có access_token. Vui lòng kết nối lại bằng OAuth Lazada trước khi đồng bộ.");
        }
        channelTokenService.getValidToken(channelId);

        SyncLog syncLog = syncLogRepository.save(SyncLog.builder()
                .channel(channel)
                .jobType("LAZADA_IMPORT")
                .status(SyncStatus.PENDING)
                .startedAt(OffsetDateTime.now())
                .build());

        int productCount = 0;
        int variantCount = 0;
        int warehouseCount = 0;

        try {
            Warehouse masterWarehouse = marketplaceWarehouseConsistencyService.resolveAndValidatePrimaryWarehouse(channel);
            String primaryWarehouseCode = optionalText(channel.getMetadata(), "lazadaWarehouseCode");
            List<JsonNode> warehouses = fetchWarehouses(credential);
            Map<String, Warehouse> warehouseByCode = new HashMap<>();
            for (JsonNode warehouseNode : warehouses) {
                warehouseCount++;

                String code = firstText(warehouseNode, "code", "id", "warehouse_id", "warehouse_code");
                if (code != null && !code.isBlank()) {
                    warehouseByCode.put(code, masterWarehouse);
                }
            }

            Map<String, String> lazadaCategoryNames = shouldLoadCategoryTree(channel)
                    ? fetchCategoryNames(credential)
                    : Map.of();
            List<JsonNode> products = fetchProducts(credential);
            Set<UUID> changedVariantIds = new HashSet<>();
            for (JsonNode productNode : products) {
                ImportedProduct imported = upsertProduct(
                        channel,
                        productNode,
                        warehouseByCode,
                        masterWarehouse,
                        primaryWarehouseCode,
                        lazadaCategoryNames,
                        syncLog
                );
                productCount += imported.productSaved ? 1 : 0;
                variantCount += imported.variantCount;
                changedVariantIds.addAll(imported.variantIds());
            }
            Map<String, Object> metadata = channel.getMetadata() == null
                    ? new HashMap<>()
                    : new HashMap<>(channel.getMetadata());
            metadata.put("productCount", channelProductRepository.countByChannelIdAndMappingState(channelId, "ACTIVE"));
            metadata.put("skuVariantCount", channelProductVariantRepository.countActiveByChannelId(channelId));
            metadata.put("warehouseCount", warehouseCount);
            channel.setMetadata(metadata);
            channel.setLastSyncedAt(OffsetDateTime.now());
            channelRepository.save(channel);

            syncLog.setStatus(SyncStatus.SYNCED);
            syncLog.setTotalItems(productCount + warehouseCount);
            syncLog.setSuccessCount(productCount + warehouseCount);
            syncLog.setFailCount(0);
            syncLog.setCompletedAt(OffsetDateTime.now());
            syncLogRepository.save(syncLog);
            marketplaceInventoryPropagationService.schedulePushAvailableStock(changedVariantIds);

            return ChannelImportSyncResponse.builder()
                    .channelId(channelId)
                    .syncLogId(syncLog.getId())
                    .productCount(productCount)
                    .variantCount(variantCount)
                    .warehouseCount(warehouseCount)
                    .status(SyncStatus.SYNCED.name())
                    .message("Đồng bộ Lazada thành công.")
                    .build();
        } catch (Exception e) {
            log.error("[LazadaImportSync] Failed to sync channel {}", channelId, e);
            syncLog.setStatus(SyncStatus.FAILED);
            syncLog.setTotalItems(productCount + warehouseCount);
            syncLog.setSuccessCount(productCount + warehouseCount);
            syncLog.setFailCount(1);
            syncLog.setErrorSummary(e.getMessage());
            syncLog.setCompletedAt(OffsetDateTime.now());
            syncLog = syncLogRepository.save(syncLog);
            syncAlertService.notifySyncFailure(syncLog);
            throw e;
        }
    }

    private List<JsonNode> fetchProducts(ChannelCredential credential) {
        List<JsonNode> products = new ArrayList<>();
        int offset = 0;

        while (true) {
            Map<String, String> params = new HashMap<>();
            params.put("filter", "all");
            params.put("limit", String.valueOf(PRODUCT_PAGE_SIZE));
            params.put("offset", String.valueOf(offset));
            params.put("options", "1");

            String response = lazadaApiClient.executeGet(credential.getChannel().getId(),
                    "/products/get",
                    params
            );

            JsonNode root = readTree(response);
            ensureLazadaSuccess(root, "/products/get");

            List<JsonNode> pageProducts = toList(firstExisting(root,
                    "/data/products",
                    "/data/product",
                    "/products"
            ));
            products.addAll(pageProducts);

            if (pageProducts.size() < PRODUCT_PAGE_SIZE) {
                break;
            }
            offset += PRODUCT_PAGE_SIZE;
        }

        return products;
    }

    private List<JsonNode> fetchWarehouses(ChannelCredential credential) {
        String response = lazadaApiClient.executeGet(credential.getChannel().getId(),
                "/rc/warehouse/get",
                Map.of()
        );

        JsonNode root = readTree(response);
        ensureLazadaSuccess(root, "/rc/warehouse/get");
        return toList(firstExisting(root,
                "/result/module",
                "/data/warehouses",
                "/data/warehouse_list",
                "/data/warehouse",
                "/warehouses"
        ));
    }

    private Map<String, String> fetchCategoryNames(ChannelCredential credential) {
        try {
            String response = lazadaApiClient.executeGet(credential.getChannel().getId(),
                    "/category/tree/get",
                    Map.of()
            );
            JsonNode root = readTree(response);
            ensureLazadaSuccess(root, "/category/tree/get");
            Map<String, String> categoryNames = new HashMap<>();
            collectCategoryNames(firstExisting(root, "/data", "/result", "/categories", "/category"), categoryNames);
            return categoryNames;
        } catch (Exception e) {
            log.warn("[LazadaImportSync] Cannot load Lazada category tree, category names may use fallback IDs: {}", e.getMessage());
            return Map.of();
        }
    }

    private boolean shouldLoadCategoryTree(Channel channel) {
        if (channel.getMetadata() == null) {
            return false;
        }
        Object value = channel.getMetadata().get("syncCategoryTree");
        return value instanceof Boolean bool ? bool : value != null && Boolean.parseBoolean(value.toString());
    }

    private void collectCategoryNames(JsonNode node, Map<String, String> categoryNames) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> collectCategoryNames(child, categoryNames));
            return;
        }
        if (!node.isObject()) {
            return;
        }

        String id = firstText(node, "category_id", "categoryId", "id", "CategoryId");
        String name = firstText(node, "name", "category_name", "categoryName", "Name");
        if (id != null && !id.isBlank() && name != null && !name.isBlank()) {
            categoryNames.put(id, name);
        }

        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            JsonNode value = fields.next().getValue();
            if (value.isArray() || value.isObject()) {
                collectCategoryNames(value, categoryNames);
            }
        }
    }

    private ImportedProduct upsertProduct(Channel channel,
                                          JsonNode productNode,
                                          Map<String, Warehouse> warehouseByCode,
                                          Warehouse masterWarehouse,
                                          String primaryWarehouseCode,
                                          Map<String, String> lazadaCategoryNames,
                                          SyncLog syncLog) {
        String externalProductId = firstText(productNode, "item_id", "product_id", "id");
        if (externalProductId == null || externalProductId.isBlank()) {
            externalProductId = UUID.randomUUID().toString();
        }
        final String resolvedExternalProductId = externalProductId;

        ChannelProduct channelProduct = channelProductRepository
                .findByChannelIdAndExternalProductId(channel.getId(), externalProductId)
                .orElseGet(ChannelProduct::new);

        Product product = extractSkus(productNode).stream()
                    .map(this::resolveSellerSku)
                    .filter(value -> value != null && !value.isBlank())
                    .map(productVariantRepository::findBySkuAndDeletedAtIsNull)
                    .flatMap(java.util.Optional::stream)
                    .map(ProductVariant::getProduct)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(channelProduct.getProduct());
        if (product == null) {
            String productName = resolveProductName(productNode, resolvedExternalProductId);
            product = java.util.Optional.<Product>empty()
                    .or(() -> productRepository.findFirstByNameIgnoreCaseAndDeletedAtIsNullOrderByCreatedAtAsc(productName))
                    .or(() -> productRepository.findFirstBySkuAndDeletedAtIsNull(fallbackSku(resolvedExternalProductId)))
                    .orElseGet(Product::new);
        }

        boolean lazadaOwned = product.getId() == null || isGeneratedPlatformSku(product.getSku());
        product.setSku(product.getSku() == null ? firstSellerSku(productNode, fallbackSku(externalProductId)) : product.getSku());
        if (lazadaOwned) {
            product.setName(resolveProductName(productNode, externalProductId));
            product.setDescription(firstNonBlank(
                    firstText(productNode, "description", "short_description"),
                    productNode.path("attributes").path("description").asText(null)
            ));
            product.setBrand(resolveBrand(productNode));
            product.setStatus(resolveProductStatus(firstText(productNode, "status", "seller_status")));
        }
        product.setUnit(product.getUnit() == null ? "pcs" : product.getUnit());
        Map<String, Object> productAttributes = product.getAttributes() == null
                ? new HashMap<>()
                : new HashMap<>(product.getAttributes());
        productAttributes.put("lazadaProduct", toMap(productNode));
        product.setAttributes(productAttributes);
        Category category = resolveCategory(productNode, lazadaCategoryNames);
        if (category != null) {
            product.setCategory(category);
        }
        product = productRepository.save(product);

        channelProduct.setChannel(channel);
        channelProduct.setProduct(product);
        channelProduct.setExternalProductId(externalProductId);
        channelProduct.setExternalStatus(firstText(productNode, "status", "seller_status"));
        channelProduct.setMappingState("ACTIVE");
        channelProduct.setSyncStatus(SyncStatus.SYNCED);
        channelProduct.setLastSyncedAt(OffsetDateTime.now());
        channelProduct.setLastSyncError(null);
        channelProduct = channelProductRepository.save(channelProduct);
        channelProduct = channelProductAggregationService.normalizeImportedMapping(channelProduct);

        int variantCount = 0;
        Set<UUID> variantIds = new HashSet<>();
        for (JsonNode skuNode : extractSkus(productNode)) {
            ProductVariant variant = upsertVariant(channelProduct, product, skuNode, externalProductId, variantCount);
            upsertChannelVariant(channelProduct, variant, skuNode, variantCount);
            upsertInventoryItems(variant, skuNode, warehouseByCode, masterWarehouse, primaryWarehouseCode, syncLog);
            variantIds.add(variant.getId());
            variantCount++;
        }

        if (variantCount == 0) {
            ProductVariant variant = upsertFallbackVariant(channelProduct, product, externalProductId);
            upsertChannelVariant(channelProduct, variant, productNode, 0);
            upsertInventoryItems(variant, productNode, warehouseByCode, masterWarehouse, primaryWarehouseCode, syncLog);
            variantIds.add(variant.getId());
            variantCount = 1;
        }

        return new ImportedProduct(true, variantCount, variantIds);
    }

    private ProductVariant upsertVariant(ChannelProduct channelProduct,
                                         Product product,
                                         JsonNode skuNode,
                                         String externalProductId,
                                         int index) {
        String externalVariantId = resolveExternalVariantId(skuNode, externalProductId, index);
        String sellerSku = resolveSellerSku(skuNode);
        String localSku = resolveLocalVariantSku(channelProduct, sellerSku, externalProductId, externalVariantId);

        ProductVariant variant = productVariantRepository.findBySkuAndDeletedAtIsNull(sellerSku)
                .filter(existing -> existing.getProduct() != null
                        && Objects.equals(existing.getProduct().getId(), product.getId()))
                .or(() -> channelProductVariantRepository
                        .findByChannelProductIdAndExternalVariantId(channelProduct.getId(), externalVariantId)
                        .map(ChannelProductVariant::getVariant)
                        .filter(existing -> shouldReuseMappedVariant(channelProduct, existing)))
                .orElseGet(() -> productVariantRepository.findByProductIdAndSkuAndDeletedAtIsNull(product.getId(), localSku)
                        .orElseGet(ProductVariant::new));
        variant.setProduct(product);
        variant.setSku(localSku);
        String variantName = firstNonBlank(resolveVariantName(skuNode), product.getName());
        variant.setName(variantName);
        variant.setBarcode(firstText(skuNode, "barcode", "BarCode", "bar_code"));
        variant.setPrice(firstDecimal(skuNode, "price", "special_price", "sale_price", "salePrice"));
        variant.setIsActive(true);
        variant.setOptionValues(resolveOptionValues(skuNode, variantName, product.getName()));
        variant.setWeightGrams(firstInteger(skuNode, "package_weight", "packageWeight", "weight"));
        return productVariantRepository.save(variant);
    }

    private ProductVariant upsertFallbackVariant(ChannelProduct channelProduct, Product product, String externalProductId) {
        String sku = resolveLocalVariantSku(channelProduct, null, externalProductId, "DEFAULT");
        ProductVariant variant = productVariantRepository.findByProductIdAndSkuAndDeletedAtIsNull(product.getId(), sku)
                .orElseGet(ProductVariant::new);
        variant.setProduct(product);
        variant.setSku(sku);
        variant.setName(product.getName());
        variant.setPrice(BigDecimal.ZERO);
        variant.setIsActive(true);
        variant.setOptionValues(Map.of("default", true));
        return productVariantRepository.save(variant);
    }

    private String resolveLocalVariantSku(ChannelProduct channelProduct,
                                          String sellerSku,
                                          String externalProductId,
                                          String externalVariantId) {
        String fallbackSku = fallbackSku(firstNonBlank(externalVariantId, externalProductId));
        String baseSku = truncateSku(firstNonBlank(sellerSku, fallbackSku));
        if (isSkuUsableForExternalVariant(channelProduct, baseSku, externalVariantId)) {
            return baseSku;
        }

        String suffixToken = skuSuffixToken(externalVariantId, externalProductId);
        String candidate = appendSkuSuffix(baseSku, suffixToken, 1);
        int suffix = 2;
        while (!isSkuUsableForExternalVariant(channelProduct, candidate, externalVariantId)) {
            candidate = appendSkuSuffix(baseSku, suffixToken, suffix++);
        }
        return candidate;
    }

    private String firstSellerSku(JsonNode productNode, String fallback) {
        return extractSkus(productNode).stream()
                .map(this::resolveSellerSku)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(fallback);
    }

    private String fallbackSku(String externalId) {
        return "EXT-" + firstNonBlank(externalId, UUID.randomUUID().toString());
    }

    private boolean isGeneratedPlatformSku(String sku) {
        return sku != null && (sku.startsWith("SHOPIFY-") || sku.startsWith("LAZADA-") || sku.startsWith("TIKTOK-"));
    }

    private boolean isSkuUsableForExternalVariant(ChannelProduct channelProduct, String sku, String externalVariantId) {
        return productVariantRepository.findBySkuAndDeletedAtIsNull(sku)
                .map(existing -> {
                    if (existing.getId() == null) {
                        return true;
                    }

                    if (existing.getProduct() != null
                            && channelProduct.getProduct() != null
                            && Objects.equals(existing.getProduct().getId(), channelProduct.getProduct().getId())) {
                        return true;
                    }

                    List<ChannelProductVariant> mappings = channelProductVariantRepository
                            .findActiveByVariantIdWithChannel(existing.getId());
                    if (mappings.isEmpty()) {
                        return existing.getProduct() != null
                                && channelProduct.getProduct() != null
                                && Objects.equals(existing.getProduct().getId(), channelProduct.getProduct().getId());
                    }

                    List<ChannelProductVariant> currentMappings = mappings.stream()
                            .filter(mapping -> mapping.getChannelProduct() != null)
                            .filter(mapping -> Objects.equals(mapping.getChannelProduct().getId(), channelProduct.getId()))
                            .toList();
                    if (currentMappings.isEmpty() || currentMappings.size() != mappings.size()) {
                        return false;
                    }
                    return currentMappings.stream()
                            .anyMatch(mapping -> Objects.equals(mapping.getExternalVariantId(), externalVariantId));
                })
                .orElse(true);
    }

    private boolean shouldReuseMappedVariant(ChannelProduct channelProduct, ProductVariant existingVariant) {
        if (existingVariant == null || existingVariant.getId() == null) {
            return false;
        }
        return channelProductVariantRepository.findActiveByVariantIdWithChannel(existingVariant.getId())
                .stream()
                .allMatch(mapping -> mapping.getChannelProduct() != null
                        && Objects.equals(mapping.getChannelProduct().getId(), channelProduct.getId()));
    }

    private String appendSkuSuffix(String baseSku, String suffixToken, int suffix) {
        String suffixValue = "-" + suffixToken + (suffix > 1 ? "-" + suffix : "");
        if (suffixValue.length() >= MAX_VARIANT_SKU_LENGTH) {
            suffixValue = suffixValue.substring(suffixValue.length() - MAX_VARIANT_SKU_LENGTH + 1);
            if (!suffixValue.startsWith("-")) {
                suffixValue = "-" + suffixValue;
            }
        }
        int baseLength = Math.max(1, MAX_VARIANT_SKU_LENGTH - suffixValue.length());
        return truncateSku(baseSku, baseLength) + suffixValue;
    }

    private String skuSuffixToken(String... values) {
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            String token = value.trim().replaceAll("[^A-Za-z0-9_-]+", "-");
            if (token.length() > 40) {
                token = token.substring(token.length() - 40);
            }
            if (!token.isBlank()) {
                return token;
            }
        }
        return "SKU";
    }

    private String truncateSku(String sku) {
        return truncateSku(sku, MAX_VARIANT_SKU_LENGTH);
    }

    private String truncateSku(String sku, int maxLength) {
        String normalized = sku == null ? "" : sku.trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength);
    }

    private void upsertChannelVariant(ChannelProduct channelProduct, ProductVariant variant, JsonNode skuNode, int index) {
        String externalVariantId = resolveExternalVariantId(skuNode, channelProduct.getExternalProductId(), index);

        ChannelProductVariant channelVariant = resolveChannelVariantMapping(channelProduct, variant, externalVariantId);
        channelVariant.setChannelProduct(channelProduct);
        channelVariant.setVariant(variant);
        channelVariant.setExternalSku(firstNonBlank(firstText(skuNode,
                "SellerSku",
                "seller_sku",
                "sellerSku",
                "sellerSKU"
        ), variant.getSku()));
        channelVariant.setExternalPrice(firstDecimal(skuNode, "price", "special_price", "sale_price"));
        channelVariant.setSyncStatus(SyncStatus.SYNCED);
        channelVariant.setLastSyncedAt(OffsetDateTime.now());
        channelVariant.setMetadata(toMap(skuNode));
        applyExternalVariantId(channelProduct, channelVariant, externalVariantId);
        channelProductVariantRepository.save(channelVariant);
    }

    private ChannelProductVariant resolveChannelVariantMapping(ChannelProduct channelProduct,
                                                               ProductVariant variant,
                                                               String externalVariantId) {
        return channelProductVariantRepository
                .findByChannelProductIdAndVariantId(channelProduct.getId(), variant.getId())
                .or(() -> channelProductVariantRepository
                        .findByChannelProductIdAndExternalVariantId(channelProduct.getId(), externalVariantId))
                .orElseGet(ChannelProductVariant::new);
    }

    private void applyExternalVariantId(ChannelProduct channelProduct,
                                        ChannelProductVariant channelVariant,
                                        String externalVariantId) {
        channelProductVariantRepository
                .findByChannelProductIdAndExternalVariantId(channelProduct.getId(), externalVariantId)
                .filter(existing -> channelVariant.getId() != null && !Objects.equals(existing.getId(), channelVariant.getId()))
                .ifPresentOrElse(
                        existing -> {
                            Map<String, Object> metadata = channelVariant.getMetadata() == null
                                    ? new HashMap<>()
                                    : new HashMap<>(channelVariant.getMetadata());
                            metadata.put("conflictingLazadaExternalVariantId", externalVariantId);
                            metadata.put("conflictingChannelProductVariantId", existing.getId().toString());
                            channelVariant.setMetadata(metadata);
                        },
                        () -> channelVariant.setExternalVariantId(externalVariantId)
                );
    }

    private String resolveExternalVariantId(JsonNode skuNode, String externalProductId, int index) {
        String externalVariantId = firstText(skuNode,
                "SkuId", "sku_id", "skuId",
                "ShopSku", "shop_sku", "shopSku",
                "SellerSku", "seller_sku", "sellerSku"
        );
        if (externalVariantId == null || externalVariantId.isBlank()) {
            externalVariantId = externalProductId + "-SKU-" + index;
        }
        return externalVariantId;
    }

    private String resolveSellerSku(JsonNode skuNode) {
        return firstText(skuNode,
                "SellerSku",
                "seller_sku",
                "sellerSku",
                "sellerSKU",
                "seller_sku_id"
        );
    }

    private String resolveVariantName(JsonNode skuNode) {
        String directName = firstText(skuNode,
                "name",
                "Name",
                "SkuName",
                "sku_name",
                "skuName",
                "variationName",
                "variation_name"
        );
        if (directName != null && !directName.isBlank()) {
            return directName;
        }

        Map<String, Object> attributes = toMap(skuNode);
        String knownOptionName = firstNonBlank(
                stringValue(attributes.get("color_family")),
                stringValue(attributes.get("ColorFamily")),
                stringValue(attributes.get("color")),
                stringValue(attributes.get("Color")),
                stringValue(attributes.get("size")),
                stringValue(attributes.get("Size")),
                stringValue(attributes.get("model")),
                stringValue(attributes.get("Model"))
        );
        if (knownOptionName != null && !knownOptionName.isBlank() && !looksLikeTechnicalValue(knownOptionName)) {
            return knownOptionName;
        }

        String propertyName = firstVariantPropertyText(skuNode);
        if (propertyName != null && !propertyName.isBlank()) {
            return propertyName;
        }

        return null;
    }

    private Map<String, Object> resolveOptionValues(JsonNode skuNode, String variantName, String productName) {
        Map<String, Object> optionValues = new java.util.LinkedHashMap<>();
        String color = firstNonBlank(
                firstText(skuNode, "color_family", "ColorFamily", "color", "Color", "colour", "Colour"),
                isUsableOptionValue(variantName) && !variantName.equals(productName) ? variantName : null
        );
        if (isUsableOptionValue(color)) {
            optionValues.put("Màu", color);
            optionValues.put("color_family", color);
        }

        String size = firstText(skuNode, "size", "Size", "model", "Model");
        if (isUsableOptionValue(size) && !size.equals(color)) {
            optionValues.put("Size", size);
        }

        if (optionValues.isEmpty() && isUsableOptionValue(variantName) && !variantName.equals(productName)) {
            optionValues.put("Phân loại", variantName);
        }

        return optionValues;
    }

    private String firstVariantPropertyText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }

        if (node.isObject()) {
            String value = firstText(node,
                    "value",
                    "Value",
                    "valueName",
                    "value_name",
                    "propertyValue",
                    "property_value",
                    "name",
                    "Name"
            );
            if (value != null && !value.isBlank() && !looksLikeTechnicalValue(value)) {
                return value;
            }

            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String nested = firstVariantPropertyText(field.getValue());
                if (nested != null && !nested.isBlank()) {
                    return nested;
                }
            }
        }

        if (node.isArray()) {
            for (JsonNode child : node) {
                String nested = firstVariantPropertyText(child);
                if (nested != null && !nested.isBlank()) {
                    return nested;
                }
            }
        }

        return null;
    }

    private boolean looksLikeTechnicalValue(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String normalized = value.toLowerCase();
        return normalized.contains("sku")
                || normalized.contains("http")
                || normalized.matches("^[a-z]{1,6}-?\\d{1,8}$")
                || normalized.matches("^[a-z0-9_\\-]{12,}$")
                || normalized.matches("\\d+")
                || normalized.length() > 80;
    }

    private boolean isUsableOptionValue(String value) {
        return value != null && !value.isBlank() && !looksLikeTechnicalValue(value);
    }

    private Warehouse upsertWarehouse(JsonNode warehouseNode) {
        String externalWarehouseId = firstText(warehouseNode, "id", "warehouse_id", "warehouse_code", "code");
        String name = firstText(warehouseNode, "name", "warehouse_name", "warehouseName");
        if (name == null || name.isBlank()) {
            name = externalWarehouseId == null ? "Lazada Warehouse" : "Lazada Warehouse " + externalWarehouseId;
        }

        Warehouse warehouse = warehouseRepository.findFirstByNameAndDeletedAtIsNull(name)
                .orElseGet(Warehouse::new);
        warehouse.setName(name);
        warehouse.setAddress(withWarehouseCodeMarker(
                firstText(warehouseNode, "detailAddress", "address", "warehouse_address"),
                externalWarehouseId
        ));
        String status = firstText(warehouseNode, "status");
        warehouse.setIsActive(status == null || status.equalsIgnoreCase("ACTIVE"));
        warehouseRepository.save(warehouse);
        return warehouse;
    }

    private void upsertInventoryItems(ProductVariant variant,
                                      JsonNode skuNode,
                                      Map<String, Warehouse> warehouseByCode,
                                      Warehouse masterWarehouse,
                                      String primaryWarehouseCode,
                                      SyncLog syncLog) {
        List<JsonNode> warehouseInventories = toList(firstExisting(skuNode,
                "/multiWarehouseInventories",
                "/channelInventories",
                "/fblWarehouseInventories",
                "/warehouseInventories",
                "/warehouses",
                "/stock_list"
        ));
        if (warehouseInventories.isEmpty()) {
            warehouseInventories = firstArrayByName(skuNode,
                    "multiWarehouseInventories",
                    "channelInventories",
                    "fblWarehouseInventories",
                    "warehouseInventories",
                    "warehouses",
                    "stock_list"
            );
        }

        if (warehouseInventories.isEmpty()) {
            Integer quantity = firstInteger(skuNode,
                    "quantity",
                    "Quantity",
                    "available",
                    "Available",
                    "sellableStock",
                    "sellable_stock",
                    "availableStock",
                    "available_stock",
                    "availableQuantity",
                    "available_quantity",
                    "stock",
                    "Stock"
            );
            if (quantity == null) {
                return;
            }

            upsertInventoryItem(masterWarehouse, variant, quantity, 0, syncLog);
            return;
        }

        Integer bestQuantityOnHand = null;
        int bestReservedQuantity = 0;
        for (JsonNode inventoryNode : warehouseInventories) {
            String warehouseCode = firstText(inventoryNode,
                    "warehouseCode",
                    "warehouse_code",
                    "warehouseId",
                    "warehouse_id",
                    "code",
                    "id"
            );

            Integer totalQuantity = firstInteger(inventoryNode,
                    "totalQuantity",
                    "total_quantity",
                    "quantityOnHand",
                    "quantity_on_hand",
                    "quantity",
                    "Quantity"
            );
            Integer availableQuantity = firstInteger(inventoryNode,
                    "sellableQuantity",
                    "sellable_quantity",
                    "sellableStock",
                    "sellable_stock",
                    "availableStock",
                    "available_stock",
                    "availableQuantity",
                    "available_quantity",
                    "available",
                    "Available",
                    "stock",
                    "Stock"
            );
            int reservedQuantity = firstIntegerOrDefault(inventoryNode, 0,
                    "occupyQuantity",
                    "occupy_quantity",
                    "reservedQuantity",
                    "reserved_quantity",
                    "reserved"
            )
                    + firstIntegerOrDefault(inventoryNode, 0,
                    "withholdQuantity",
                    "withhold_quantity",
                    "holdQuantity",
                    "hold_quantity"
            );
            Integer quantityOnHand = totalQuantity != null ? totalQuantity : availableQuantity;
            if (quantityOnHand == null) {
                continue;
            }
            if (totalQuantity == null && reservedQuantity > 0) {
                quantityOnHand += reservedQuantity;
            }

            if (reservedQuantity > quantityOnHand) {
                reservedQuantity = quantityOnHand;
            }
            if (bestQuantityOnHand == null || quantityOnHand > bestQuantityOnHand) {
                bestQuantityOnHand = quantityOnHand;
                bestReservedQuantity = reservedQuantity;
            }
        }
        if (bestQuantityOnHand != null) {
            upsertInventoryItem(masterWarehouse, variant, bestQuantityOnHand, bestReservedQuantity, syncLog);
        }
    }

    private Warehouse resolveWarehouse(String warehouseCode, Map<String, Warehouse> warehouseByCode) {
        String normalizedCode = firstNonBlank(warehouseCode, "dropshipping");
        Warehouse warehouse = warehouseByCode.get(normalizedCode);
        if (warehouse != null) {
            return warehouse;
        }

        warehouse = warehouseRepository.findFirstByNameAndDeletedAtIsNull(normalizedCode)
                .orElseGet(() -> Warehouse.builder()
                        .name(normalizedCode)
                        .address("Lazada warehouse code: " + normalizedCode)
                        .isActive(true)
                        .build());
        warehouse = warehouseRepository.save(warehouse);
        warehouseByCode.put(normalizedCode, warehouse);
        return warehouse;
    }

    private void upsertInventoryItem(Warehouse warehouse,
                                     ProductVariant variant,
                                     int quantityOnHand,
                                     int reservedQuantity,
                                     SyncLog syncLog) {
        InventoryItem item = inventoryItemRepository
                .findByWarehouseIdAndVariantId(warehouse.getId(), variant.getId())
                .orElseGet(() -> InventoryItem.builder()
                        .warehouse(warehouse)
                        .variant(variant)
                        .lowStockThreshold(variant.getProduct().getLowStockThreshold())
                        .build());

        int quantityBefore = item.getQuantityOnHand() == null ? 0 : item.getQuantityOnHand();
        int quantityAfter = Math.max(quantityOnHand, 0);
        item.setQuantityOnHand(quantityAfter);
        item.setReservedQuantity(Math.max(reservedQuantity, 0));
        if (item.getLowStockThreshold() == null) {
            item.setLowStockThreshold(variant.getProduct().getLowStockThreshold());
        }
        inventoryItemRepository.save(item);
        recordInventorySyncTransaction(syncLog, warehouse, variant, quantityBefore, quantityAfter, "Lazada");
    }

    private void recordInventorySyncTransaction(SyncLog syncLog,
                                                Warehouse warehouse,
                                                ProductVariant variant,
                                                int quantityBefore,
                                                int quantityAfter,
                                                String platform) {
        int quantityChange = quantityAfter - quantityBefore;
        if (quantityChange == 0) {
            return;
        }

        inventoryTransactionRepository.save(InventoryTransaction.builder()
                .warehouse(warehouse)
                .variant(variant)
                .type(InvTxnType.ADJUSTMENT)
                .referenceType("ADJUSTMENT")
                .referenceId(syncLog.getId())
                .quantityChange(quantityChange)
                .quantityBefore(quantityBefore)
                .quantityAfter(quantityAfter)
                .unitCost(BigDecimal.ZERO)
                .note("Đồng bộ tồn kho từ " + platform)
                .performedAt(OffsetDateTime.now())
                .build());
    }

    private List<JsonNode> extractSkus(JsonNode productNode) {
        JsonNode skus = firstExisting(productNode,
                "/skus",
                "/Skus",
                "/SKUs",
                "/sku_list",
                "/skuList",
                "/data/skus",
                "/data/Skus"
        );
        List<JsonNode> skuList = toList(skus);
        if (!skuList.isEmpty()) {
            return skuList;
        }
        return firstArrayByName(productNode, "skus", "Skus", "SKUs", "sku_list", "skuList");
    }

    private JsonNode readTree(String response) {
        try {
            return objectMapper.readTree(response);
        } catch (Exception e) {
            throw new IllegalStateException("Không đọc được response từ Lazada.", e);
        }
    }

    private void ensureLazadaSuccess(JsonNode root, String apiPath) {
        String code = root.path("code").asText("");
        if (!code.isBlank() && !"0".equals(code)) {
            String message = firstText(root, "message", "msg", "error_msg");
            throw new IllegalStateException("Lazada API " + apiPath + " lỗi: " + message);
        }
    }

    private Long tokenExpiresAt(ChannelCredential credential) {
        return credential.getTokenExpiresAt() == null ? null : credential.getTokenExpiresAt().toEpochSecond();
    }

    private JsonNode firstExisting(JsonNode node, String... pointers) {
        for (String pointer : pointers) {
            JsonNode value = node.at(pointer);
            if (!value.isMissingNode() && !value.isNull()) {
                return value;
            }
        }
        return objectMapper.createArrayNode();
    }

    private List<JsonNode> toList(JsonNode node) {
        List<JsonNode> list = new ArrayList<>();
        if (node == null || node.isMissingNode() || node.isNull()) {
            return list;
        }
        if (node.isArray()) {
            node.forEach(list::add);
            return list;
        }
        if (node.isObject()) {
            Iterator<JsonNode> values = node.elements();
            while (values.hasNext()) {
                JsonNode value = values.next();
                if (value.isObject()) {
                    list.add(value);
                }
            }
        }
        return list;
    }

    private String resolveProductName(JsonNode productNode, String externalProductId) {
        String attrName = productNode.path("attributes").path("name").asText(null);
        return firstNonBlank(
                firstText(productNode, "name", "item_name", "product_name"),
                attrName,
                "Lazada Product " + externalProductId
        );
    }

    private String resolveBrand(JsonNode productNode) {
        return firstNonBlank(
                firstText(productNode, "brand", "Brand"),
                productNode.path("attributes").path("brand").asText(null)
        );
    }

    private Category resolveCategory(JsonNode productNode, Map<String, String> lazadaCategoryNames) {
        String externalCategoryId = firstNonBlank(
                firstText(productNode,
                        "PrimaryCategory",
                        "primary_category",
                        "primaryCategory",
                        "category_id",
                        "categoryId",
                        "leaf_category_id",
                        "leafCategoryId"),
                productNode.path("attributes").path("PrimaryCategory").asText(null),
                productNode.path("attributes").path("primary_category").asText(null),
                productNode.path("attributes").path("category_id").asText(null)
        );
        String categoryName = firstNonBlank(
                firstText(productNode,
                        "category_name",
                        "categoryName",
                        "primary_category_name",
                        "primaryCategoryName",
                        "leaf_category_name",
                        "leafCategoryName",
                        "category_path",
                        "categoryPath"),
                productNode.path("attributes").path("category_name").asText(null),
                productNode.path("attributes").path("categoryName").asText(null),
                productNode.path("attributes").path("primary_category_name").asText(null),
                productNode.path("attributes").path("leaf_category_name").asText(null)
        );

        if ((externalCategoryId == null || externalCategoryId.isBlank())
                && (categoryName == null || categoryName.isBlank())) {
            return null;
        }

        String categoryTreeName = externalCategoryId == null ? null : lazadaCategoryNames.get(externalCategoryId);
        String slug = externalCategoryId != null && !externalCategoryId.isBlank()
                ? "lazada-" + toSlug(externalCategoryId)
                : "lazada-" + toSlug(categoryName);
        String resolvedName = firstNonBlank(categoryName, categoryTreeName, "Lazada " + externalCategoryId);

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

    private ProductStatus resolveProductStatus(String externalStatus) {
        if (externalStatus == null) {
            return ProductStatus.ACTIVE;
        }
        String normalized = externalStatus.toLowerCase();
        if (normalized.contains("inactive") || normalized.contains("deleted")) {
            return ProductStatus.INACTIVE;
        }
        return ProductStatus.ACTIVE;
    }

    private String firstText(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull() && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            for (String name : names) {
                if (field.getKey().equalsIgnoreCase(name)) {
                    JsonNode value = field.getValue();
                    if (value != null && !value.isNull() && !value.asText().isBlank()) {
                        return value.asText();
                    }
                }
            }
        }
        return null;
    }

    private BigDecimal firstDecimal(JsonNode node, String... names) {
        String value = firstText(node, names);
        if (value == null) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private Integer firstInteger(JsonNode node, String... names) {
        String value = firstText(node, names);
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value).intValue();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int firstIntegerOrDefault(JsonNode node, int fallback, String... names) {
        Integer value = firstInteger(node, names);
        return value == null ? fallback : value;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private String optionalText(Map<String, Object> metadata, String key) {
        if (metadata == null) {
            return null;
        }
        Object value = metadata.get(key);
        return value == null || value.toString().isBlank() ? null : value.toString();
    }

    private String toSlug(String value) {
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

    private String withWarehouseCodeMarker(String address, String warehouseCode) {
        if (warehouseCode == null || warehouseCode.isBlank()) {
            return address;
        }

        String marker = "[" + WAREHOUSE_CODE_MARKER + warehouseCode + "]";
        if (address == null || address.isBlank()) {
            return marker;
        }
        int markerIndex = address.indexOf("[" + WAREHOUSE_CODE_MARKER);
        if (markerIndex >= 0) {
            return address.substring(0, markerIndex).trim() + " " + marker;
        }
        return address.trim() + " " + marker;
    }

    private List<JsonNode> firstArrayByName(JsonNode node, String... names) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return List.of();
        }

        if (node.isArray()) {
            return toList(node);
        }

        if (!node.isObject()) {
            return List.of();
        }

        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            for (String name : names) {
                if (field.getKey().equalsIgnoreCase(name)) {
                    List<JsonNode> values = toList(field.getValue());
                    if (!values.isEmpty()) {
                        return values;
                    }
                }
            }
        }

        fields = node.fields();
        while (fields.hasNext()) {
            List<JsonNode> values = firstArrayByName(fields.next().getValue(), names);
            if (!values.isEmpty()) {
                return values;
            }
        }
        return List.of();
    }

    private Map<String, Object> toMap(JsonNode node) {
        return objectMapper.convertValue(node, new TypeReference<>() {});
    }

    private record ImportedProduct(boolean productSaved, int variantCount, Set<UUID> variantIds) {
    }
}
