package fu.osms.sync.shopify.impl;

import fu.osms.catalog.entity.Product;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.catalog.entity.Category;
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
import fu.osms.inventory.entity.InventoryItem;
import fu.osms.inventory.entity.InventoryTransaction;
import fu.osms.inventory.entity.Warehouse;
import fu.osms.inventory.enums.InvTxnType;
import fu.osms.inventory.repository.InventoryItemRepository;
import fu.osms.inventory.repository.InventoryTransactionRepository;
import fu.osms.inventory.repository.WarehouseRepository;
import fu.osms.sync.entity.SyncLog;
import fu.osms.sync.repository.SyncLogRepository;
import fu.osms.sync.service.MarketplaceInventoryPropagationService;
import fu.osms.sync.service.MarketplaceWarehouseConsistencyService;
import fu.osms.sync.service.impl.ChannelProductAggregationService;
import fu.osms.sync.shopify.ShopifyApiClient;
import fu.osms.sync.shopify.ShopifyImportSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShopifyImportSyncServiceImpl implements ShopifyImportSyncService {

    private static final int VARIANT_PAGE_SIZE = 100;
    private static final int INVENTORY_LEVEL_PAGE_SIZE = 50;
    private static final String DEFAULT_WAREHOUSE_NAME = "Shopify";
    private static final String DEFAULT_WAREHOUSE_KEY = "__shopify_default__";
    private static final String LOCATION_ID_MARKER = "SHOPIFY_LOCATION_ID=";

    private final ShopifyApiClient shopifyApiClient;
    private final ChannelRepository channelRepository;
    private final ChannelCredentialRepository credentialRepository;
    private final ChannelProductRepository channelProductRepository;
    private final ChannelProductVariantRepository channelProductVariantRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final WarehouseRepository warehouseRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final SyncLogRepository syncLogRepository;
    private final MarketplaceInventoryPropagationService marketplaceInventoryPropagationService;
    private final MarketplaceWarehouseConsistencyService marketplaceWarehouseConsistencyService;
    private final ChannelProductAggregationService channelProductAggregationService;

    @Override
    @Transactional
    public ChannelImportSyncResponse syncProductsAndInventory(UUID channelId) {
        Channel channel = channelRepository.findById(channelId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.CHANNEL_NOT_FOUND));
        if (channel.getPlatform() != PlatformType.SHOPIFY) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Chỉ hỗ trợ kéo dữ liệu Shopify cho kênh Shopify.");
        }

        ChannelCredential credential = credentialRepository.findByChannelIdAndConnectionState(channelId, "CONNECTED")
                .orElseThrow(() -> new AppException(ErrorCode.CHANNEL_NOT_CONNECTED, "Kênh Shopify chưa có token kết nối."));
        if (credential.getAccessToken() == null || credential.getAccessToken().isBlank()) {
            throw new AppException(ErrorCode.CHANNEL_NOT_CONNECTED, "Kênh Shopify chưa có access token.");
        }

        SyncLog syncLog = syncLogRepository.save(SyncLog.builder()
                .channel(channel)
                .jobType("SHOPIFY_REMOTE_IMPORT_SYNC")
                .status(SyncStatus.PENDING)
                .startedAt(OffsetDateTime.now())
                .build());

        int productCount = 0;
        int variantCount = 0;

        try {
            String shopDomain = extractShopDomain(channel);
            ensureLocationAddressScope(shopDomain, credential.getAccessToken());
            Warehouse masterWarehouse = marketplaceWarehouseConsistencyService.resolveAndValidatePrimaryWarehouse(channel);
            OffsetDateTime changedSince = channel.getLastSyncedAt();
            Map<String, Boolean> processedProductIds = new HashMap<>();
            Set<UUID> changedVariantIds = new HashSet<>();
            String cursor = null;
            boolean hasNextPage;

            do {
                Map<String, Object> variables = new HashMap<>();
                variables.put("first", VARIANT_PAGE_SIZE);
                variables.put("after", cursor);
                variables.put("query", buildUpdatedAfterQuery(changedSince));

                Map<String, Object> response = executeProductVariantsQuery(
                        shopDomain,
                        credential.getAccessToken(),
                        variables
                );
                log.info("[ShopifyImportSync] Product variants response cursor={}, pageSize={}, payload={}",
                        cursor, VARIANT_PAGE_SIZE, response);

                Map<String, Object> productVariants = nestedMap(response, "data", "productVariants");
                List<Map<String, Object>> nodes = list(productVariants, "nodes");
                for (Map<String, Object> variantNode : nodes) {
                    Map<String, Object> productNode = map(variantNode.get("product"));
                    String externalProductId = numericId(stringValue(productNode.get("id")));
                    if (externalProductId == null || externalProductId.isBlank()) {
                        continue;
                    }

                    Product product = upsertProduct(channel, productNode, variantNode);
                    ChannelProduct channelProduct = upsertChannelProduct(channel, product, productNode);

                    if (!processedProductIds.containsKey(externalProductId)) {
                        processedProductIds.put(externalProductId, true);
                        productCount++;
                    }

                    ProductVariant variant = upsertVariant(channelProduct, product, variantNode);
                    upsertChannelVariant(channelProduct, variant, variantNode);
                    upsertInventoryItems(variant, variantNode, masterWarehouse, syncLog);
                    changedVariantIds.add(variant.getId());
                    variantCount++;
                }

                Map<String, Object> pageInfo = map(productVariants.get("pageInfo"));
                hasNextPage = Boolean.TRUE.equals(pageInfo.get("hasNextPage"));
                cursor = stringValue(pageInfo.get("endCursor"));
            } while (hasNextPage);

            int warehouseCount = 1;
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
            syncLog.setTotalItems(productCount + variantCount);
            syncLog.setSuccessCount(productCount + variantCount);
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
                    .pushedVariantCount(0)
                    .status(SyncStatus.SYNCED.name())
                    .message("Đã kéo dữ liệu sản phẩm và tồn kho Shopify về ứng dụng.")
                    .build();
        } catch (Exception e) {
            log.error("[ShopifyImportSync] Lỗi kéo dữ liệu Shopify cho kênh {}", channelId, e);
            syncLog.setStatus(SyncStatus.FAILED);
            syncLog.setTotalItems(productCount + variantCount);
            syncLog.setSuccessCount(productCount + variantCount);
            syncLog.setFailCount(1);
            syncLog.setErrorSummary(e.getMessage());
            syncLog.setCompletedAt(OffsetDateTime.now());
            syncLogRepository.save(syncLog);
            throw e;
        }
    }

    private Product upsertProduct(Channel channel,
                                  Map<String, Object> productNode,
                                  Map<String, Object> variantNode) {
        String externalProductId = numericId(stringValue(productNode.get("id")));
        String externalSku = stringValue(variantNode.get("sku"));
        String sku = firstNonBlank(externalSku, fallbackSku(externalProductId));
        String productName = firstNonBlank(stringValue(productNode.get("title")), "Shopify Product " + externalProductId);
        Product product = (externalSku == null || externalSku.isBlank()
                ? java.util.Optional.<Product>empty()
                : productVariantRepository.findBySkuAndDeletedAtIsNull(externalSku).map(ProductVariant::getProduct))
                .or(() -> channelProductRepository
                        .findByChannelIdAndExternalProductId(channel.getId(), externalProductId)
                        .map(ChannelProduct::getProduct))
                .or(() -> productRepository.findFirstByNameIgnoreCaseAndDeletedAtIsNullOrderByCreatedAtAsc(productName))
                .or(() -> productRepository.findFirstBySkuAndDeletedAtIsNull(sku))
                .orElseGet(Product::new);
        boolean shopifyOwned = product.getId() == null || isGeneratedPlatformSku(product.getSku());
        if (product.getId() == null) {
            product.setSku(sku);
        }
        if (shopifyOwned) {
            product.setName(productName);
            product.setDescription(stringValue(productNode.get("descriptionHtml")));
            product.setBrand(stringValue(productNode.get("vendor")));
            product.setStatus(resolveStatus(stringValue(productNode.get("status"))));
        }
        product.setUnit(product.getUnit() == null ? "pcs" : product.getUnit());
        product.setLowStockThreshold(product.getLowStockThreshold() == null ? 5 : product.getLowStockThreshold());
        Category category = resolveCategory(productNode);
        if (category != null) {
            product.setCategory(category);
        }
        Map<String, Object> attributes = product.getAttributes() == null
                ? new HashMap<>()
                : new HashMap<>(product.getAttributes());
        attributes.put("shopifyProductId", externalProductId);
        attributes.put("productType", stringValue(productNode.get("productType")));
        product.setAttributes(attributes);
        return productRepository.save(product);
    }

    private Category resolveCategory(Map<String, Object> productNode) {
        String productType = firstNonBlank(
                stringValue(productNode.get("productType")),
                stringValue(productNode.get("product_type"))
        );
        if (productType == null || productType.isBlank()) {
            return null;
        }

        String resolvedName = productType.trim();
        String slug = "shopify-" + toSlug(resolvedName);
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

    private ChannelProduct upsertChannelProduct(Channel channel, Product product, Map<String, Object> productNode) {
        String externalProductId = numericId(stringValue(productNode.get("id")));
        ChannelProduct channelProduct = channelProductRepository
                .findByChannelIdAndExternalProductId(channel.getId(), externalProductId)
                .orElseGet(ChannelProduct::new);
        channelProduct.setChannel(channel);
        channelProduct.setProduct(product);
        channelProduct.setExternalProductId(externalProductId);
        channelProduct.setExternalStatus(stringValue(productNode.get("status")));
        channelProduct.setMappingState("ACTIVE");
        channelProduct.setSyncStatus(SyncStatus.SYNCED);
        channelProduct.setLastSyncedAt(OffsetDateTime.now());
        channelProduct.setLastSyncError(null);
        channelProduct = channelProductRepository.save(channelProduct);
        return channelProductAggregationService.normalizeImportedMapping(channelProduct);
    }

    private ProductVariant upsertVariant(ChannelProduct channelProduct, Product product, Map<String, Object> variantNode) {
        String externalVariantId = numericId(stringValue(variantNode.get("id")));
        String externalSku = stringValue(variantNode.get("sku"));
        String sku = resolveLocalVariantSku(channelProduct, externalSku, externalVariantId);
        ProductVariant variant = productVariantRepository.findBySkuAndDeletedAtIsNull(externalSku)
                .filter(existing -> existing.getProduct() != null
                        && Objects.equals(existing.getProduct().getId(), product.getId()))
                .or(() -> channelProductVariantRepository
                        .findByChannelProductIdAndExternalVariantId(channelProduct.getId(), externalVariantId)
                        .map(ChannelProductVariant::getVariant)
                        .filter(existing -> shouldReuseMappedVariant(channelProduct, externalVariantId, existing)))
                .orElseGet(() -> productVariantRepository.findByProductIdAndSkuAndDeletedAtIsNull(product.getId(), sku)
                        .orElseGet(ProductVariant::new));
        Map<String, Object> optionValues = selectedOptionValues(variantNode);
        String optionName = joinedOptionValueName(optionValues);
        variant.setProduct(product);
        variant.setSku(sku);
        variant.setName(firstNonBlank(optionName, stringValue(variantNode.get("title")), product.getName()));
        variant.setPrice(decimalValue(variantNode.get("price")));
        variant.setIsActive(true);
        variant.setOptionValues(optionValues.isEmpty() ? mapOf("title", variant.getName()) : optionValues);
        return productVariantRepository.save(variant);
    }

    private void upsertChannelVariant(ChannelProduct channelProduct, ProductVariant variant, Map<String, Object> variantNode) {
        String externalVariantId = numericId(stringValue(variantNode.get("id")));
        ChannelProductVariant channelVariant = resolveChannelVariantMapping(channelProduct, variant, externalVariantId);
        channelVariant.setChannelProduct(channelProduct);
        channelVariant.setVariant(variant);
        channelVariant.setExternalSku(firstNonBlank(stringValue(variantNode.get("sku")), variant.getSku()));
        channelVariant.setExternalPrice(variant.getPrice());
        channelVariant.setSyncStatus(SyncStatus.SYNCED);
        channelVariant.setLastSyncedAt(OffsetDateTime.now());

        Map<String, Object> metadata = mapOf("shopifyVariantGid", stringValue(variantNode.get("id")));
        Map<String, Object> inventoryItem = map(variantNode.get("inventoryItem"));
        if (inventoryItem.get("id") != null) {
            metadata.put("inventory_item_id", numericId(stringValue(inventoryItem.get("id"))));
        }
        channelVariant.setMetadata(metadata);
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
                            metadata.put("conflictingShopifyExternalVariantId", externalVariantId);
                            metadata.put("conflictingChannelProductVariantId", existing.getId().toString());
                            channelVariant.setMetadata(metadata);
                        },
                        () -> channelVariant.setExternalVariantId(externalVariantId)
                );
    }

    private String resolveLocalVariantSku(ChannelProduct channelProduct, String externalSku, String externalVariantId) {
        String baseSku = firstNonBlank(externalSku, fallbackSku(externalVariantId));
        if (isSkuUsableForExternalVariant(channelProduct, baseSku, externalVariantId)) {
            return baseSku;
        }

        String candidate = baseSku + "-" + externalVariantId;
        int suffix = 2;
        while (!isSkuUsableForExternalVariant(channelProduct, candidate, externalVariantId)) {
            candidate = baseSku + "-" + externalVariantId + "-" + suffix++;
        }
        return candidate;
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
                    if (existing.getProduct() != null
                            && channelProduct.getProduct() != null
                            && Objects.equals(existing.getProduct().getId(), channelProduct.getProduct().getId())) {
                        return true;
                    }
                    List<ChannelProductVariant> mappings = channelProductVariantRepository.findByChannelProductId(channelProduct.getId())
                            .stream()
                            .filter(mapping -> mapping.getVariant() != null)
                            .filter(mapping -> Objects.equals(mapping.getVariant().getId(), existing.getId()))
                            .toList();
                    if (mappings.isEmpty()) {
                        return false;
                    }
                    if (mappings.size() == 1) {
                        return Objects.equals(mappings.get(0).getExternalVariantId(), externalVariantId);
                    }
                    return Objects.equals(canonicalExternalVariantId(mappings), externalVariantId);
                })
                .orElse(true);
    }

    private boolean shouldReuseMappedVariant(ChannelProduct channelProduct,
                                             String externalVariantId,
                                             ProductVariant existingVariant) {
        if (existingVariant == null || existingVariant.getId() == null) {
            return false;
        }

        List<ChannelProductVariant> mappings = channelProductVariantRepository.findByChannelProductId(channelProduct.getId())
                .stream()
                .filter(mapping -> mapping.getVariant() != null)
                .filter(mapping -> Objects.equals(mapping.getVariant().getId(), existingVariant.getId()))
                .toList();
        if (mappings.size() <= 1) {
            return true;
        }
        return Objects.equals(canonicalExternalVariantId(mappings), externalVariantId);
    }

    private String canonicalExternalVariantId(List<ChannelProductVariant> mappings) {
        return mappings.stream()
                .map(ChannelProductVariant::getExternalVariantId)
                .filter(value -> value != null && !value.isBlank())
                .sorted()
                .findFirst()
                .orElse(null);
    }

    private Map<String, Object> selectedOptionValues(Map<String, Object> variantNode) {
        Object selectedOptions = variantNode.get("selectedOptions");
        if (!(selectedOptions instanceof List<?> optionList) || optionList.isEmpty()) {
            return new LinkedHashMap<>();
        }

        Map<String, Object> optionValues = new LinkedHashMap<>();
        for (Object optionValue : optionList) {
            Map<String, Object> option = map(optionValue);
            String name = stringValue(option.get("name"));
            String value = stringValue(option.get("value"));
            if (name != null && !name.isBlank() && value != null && !value.isBlank()) {
                optionValues.put(name, value);
            }
        }
        return optionValues;
    }

    private String joinedOptionValueName(Map<String, Object> optionValues) {
        if (optionValues == null || optionValues.isEmpty()) {
            return null;
        }
        String joined = optionValues.values().stream()
                .map(this::stringValue)
                .filter(value -> value != null && !value.isBlank())
                .collect(java.util.stream.Collectors.joining(" / "));
        return joined.isBlank() ? null : joined;
    }

    private void upsertInventoryItems(ProductVariant variant,
                                      Map<String, Object> variantNode,
                                      Warehouse masterWarehouse,
                                      SyncLog syncLog) {
        Map<String, Object> inventoryItem = map(variantNode.get("inventoryItem"));
        Map<String, Object> inventoryLevels = map(inventoryItem.get("inventoryLevels"));
        List<Map<String, Object>> levelNodes = list(inventoryLevels, "nodes");

        if (levelNodes.isEmpty()) {
            log.debug("[ShopifyImportSync] Variant {} không có inventory level, bỏ qua tạo kho mặc định.",
                    variant.getSku());
            return;
        }

        Integer maxAvailableQuantity = null;
        for (Map<String, Object> levelNode : levelNodes) {
            Map<String, Object> locationNode = map(levelNode.get("location"));
            String locationGid = stringValue(locationNode.get("id"));
            if (locationGid == null || locationGid.isBlank()) {
                log.debug("[ShopifyImportSync] Variant {} có inventory level không có location id, bỏ qua.",
                        variant.getSku());
                continue;
            }
            if (isFulfillmentServiceLocation(locationNode)) {
                log.debug("[ShopifyImportSync] Bỏ qua Shopify fulfillment/app location {} cho variant {}.",
                        firstNonBlank(stringValue(locationNode.get("name")), locationGid),
                        variant.getSku());
                continue;
            }
            int availableQuantity = availableQuantityFromLevel(levelNode);
            maxAvailableQuantity = maxAvailableQuantity == null
                    ? availableQuantity
                    : Math.max(maxAvailableQuantity, availableQuantity);
        }

        if (maxAvailableQuantity == null) {
            log.debug("[ShopifyImportSync] Variant {} không có shop location hợp lệ để đồng bộ tồn kho.",
                    variant.getSku());
            return;
        }
        upsertInventoryItem(masterWarehouse, variant, maxAvailableQuantity, 0, syncLog);
    }

    private void upsertInventoryItem(Warehouse warehouse,
                                     ProductVariant variant,
                                     int availableQuantity,
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
        int quantityAfter = Math.max(availableQuantity, 0);
        item.setQuantityOnHand(quantityAfter);
        item.setReservedQuantity(Math.min(Math.max(reservedQuantity, 0), quantityAfter));
        item.setAverageCost(item.getAverageCost() == null ? BigDecimal.ZERO : item.getAverageCost());
        if (item.getLowStockThreshold() == null) {
            item.setLowStockThreshold(variant.getProduct().getLowStockThreshold());
        }
        inventoryItemRepository.save(item);
        recordInventorySyncTransaction(syncLog, warehouse, variant, quantityBefore, quantityAfter, "Shopify");
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

    private Warehouse resolveDefaultWarehouse(Map<String, Warehouse> warehouseByLocationId) {
        Warehouse warehouse = warehouseByLocationId.get(DEFAULT_WAREHOUSE_KEY);
        if (warehouse != null) {
            return warehouse;
        }

        warehouse = warehouseRepository.findFirstByNameAndDeletedAtIsNull(DEFAULT_WAREHOUSE_NAME)
                .orElseGet(() -> Warehouse.builder()
                        .name(DEFAULT_WAREHOUSE_NAME)
                        .address("Kho đồng bộ từ Shopify")
                        .isActive(true)
                        .build());
        warehouse = warehouseRepository.save(warehouse);
        warehouseByLocationId.put(DEFAULT_WAREHOUSE_KEY, warehouse);
        return warehouse;
    }

    private Warehouse resolveShopifyWarehouse(Map<String, Object> locationNode, Map<String, Warehouse> warehouseByLocationId) {
        String locationGid = stringValue(locationNode.get("id"));
        if (locationGid == null || locationGid.isBlank()) {
            throw new IllegalArgumentException("Shopify location id không hợp lệ.");
        }

        String locationId = numericId(locationGid);
        Warehouse warehouse = warehouseByLocationId.get(locationId);
        if (warehouse != null) {
            return warehouse;
        }

        String locationName = firstNonBlank(stringValue(locationNode.get("name")), locationId);
        String warehouseName = truncate(DEFAULT_WAREHOUSE_NAME + " - " + locationName, 255);
        warehouse = warehouseRepository.findFirstByNameAndDeletedAtIsNull(warehouseName)
                .orElseGet(() -> Warehouse.builder()
                        .name(warehouseName)
                        .isActive(true)
                        .build());
        warehouse.setAddress(withLocationIdMarker(formatLocationAddress(map(locationNode.get("address"))), locationId));
        warehouse.setIsActive(true);
        warehouse = warehouseRepository.save(warehouse);
        warehouseByLocationId.put(locationId, warehouse);
        return warehouse;
    }

    private int availableQuantityFromLevel(Map<String, Object> levelNode) {
        Object quantities = levelNode.get("quantities");
        if (!(quantities instanceof List<?> quantityList)) {
            return 0;
        }

        for (Object quantityValue : quantityList) {
            Map<String, Object> quantity = map(quantityValue);
            if ("available".equalsIgnoreCase(stringValue(quantity.get("name")))) {
                return intValue(quantity.get("quantity"));
            }
        }
        return 0;
    }

    private String productVariantsQuery() {
        return """
                query ShopifyProductVariants($first: Int!, $after: String, $query: String) {
                  productVariants(first: $first, after: $after, query: $query) {
                    nodes {
                      id
                      title
                      sku
                      barcode
                      price
                      selectedOptions {
                        name
                        value
                      }
                      inventoryItem {
                        id
                        inventoryLevels(first: %d) {
                          nodes {
                            location {
                              id
                              name
                              isFulfillmentService
                              address {
                                address1
                                address2
                                city
                                province
                                zip
                                country
                                phone
                                formatted
                              }
                            }
                            quantities(names: ["available"]) {
                              name
                              quantity
                            }
                          }
                        }
                      }
                      product {
                        id
                        title
                        descriptionHtml
                        productType
                        vendor
                        status
                        updatedAt
                        variants(first: 1) {
                          nodes {
                            id
                          }
                        }
                      }
                    }
                    pageInfo {
                      hasNextPage
                      endCursor
                    }
                  }
                }
                """.formatted(INVENTORY_LEVEL_PAGE_SIZE);
    }

    private Map<String, Object> executeProductVariantsQuery(String shopDomain,
                                                            String accessToken,
                                                            Map<String, Object> variables) {
        Map<String, Object> response = shopifyApiClient.executeGraphQl(
                shopDomain,
                accessToken,
                productVariantsQuery(),
                variables
        );
        if (!hasGraphQlErrors(response)) {
            return response;
        }
        if (!hasLocationAccessDenied(response)) {
            ensureNoGraphQlErrors(response);
        }

        throw new AppException(ErrorCode.INVALID_REQUEST,
                "Token Shopify thiếu quyền read_locations nên không thể lấy tên/địa chỉ location. "
                        + "Vui lòng kết nối lại kênh Shopify để cấp quyền mới rồi đồng bộ lại.");
    }

    private void ensureLocationAddressScope(String shopDomain, String accessToken) {
        List<String> scopes = shopifyApiClient.listAccessScopes(shopDomain, accessToken);
        if (scopes.stream().anyMatch(scope -> "read_locations".equalsIgnoreCase(scope))) {
            return;
        }

        throw new AppException(ErrorCode.INVALID_REQUEST,
                "Token Shopify hiện tại chưa có quyền read_locations nên không thể lấy địa chỉ location. "
                        + "Hãy kết nối lại kênh Shopify để Shopify cấp token mới có quyền read_locations. "
                        + "Scopes hiện tại: " + scopes);
    }

    private boolean hasGraphQlErrors(Map<String, Object> response) {
        return response.get("errors") != null;
    }

    private boolean hasLocationAccessDenied(Map<String, Object> response) {
        Object errors = response.get("errors");
        if (!(errors instanceof List<?> errorList)) {
            return false;
        }
        return errorList.stream().anyMatch(error -> {
            String message = stringValue(error);
            return message != null
                    && message.contains("Access denied")
                    && (message.contains("read_locations") || message.contains("read_markets_home"));
        });
    }

    private boolean isFulfillmentServiceLocation(Map<String, Object> locationNode) {
        return booleanValue(locationNode.get("isFulfillmentService"));
    }

    @SuppressWarnings("unchecked")
    private String formatLocationAddress(Map<String, Object> addressNode) {
        Object formatted = addressNode.get("formatted");
        if (formatted instanceof List<?> formattedLines && !formattedLines.isEmpty()) {
            String joined = formattedLines.stream()
                    .map(this::stringValue)
                    .filter(value -> value != null && !value.isBlank())
                    .collect(java.util.stream.Collectors.joining(", "));
            if (!joined.isBlank()) {
                return joined;
            }
        }
        if (formatted instanceof String formattedString && !formattedString.isBlank()) {
            return formattedString;
        }

        String address = firstNonBlank(
                stringValue(addressNode.get("address1")),
                stringValue(addressNode.get("address2")),
                stringValue(addressNode.get("city")),
                stringValue(addressNode.get("province")),
                stringValue(addressNode.get("zip")),
                stringValue(addressNode.get("country")),
                stringValue(addressNode.get("phone"))
        );
        if (address == null) {
            return null;
        }

        return java.util.stream.Stream.of(
                        stringValue(addressNode.get("address1")),
                        stringValue(addressNode.get("address2")),
                        stringValue(addressNode.get("city")),
                        stringValue(addressNode.get("province")),
                        stringValue(addressNode.get("zip")),
                        stringValue(addressNode.get("country")),
                        stringValue(addressNode.get("phone"))
                )
                .filter(value -> value != null && !value.isBlank())
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private String buildUpdatedAfterQuery(OffsetDateTime lastSyncedAt) {
        if (lastSyncedAt == null) {
            return null;
        }
        return "updated_at:>" + lastSyncedAt.toInstant();
    }

    private ProductStatus resolveStatus(String status) {
        return "ACTIVE".equalsIgnoreCase(status) ? ProductStatus.ACTIVE : ProductStatus.INACTIVE;
    }

    private String extractShopDomain(Channel channel) {
        if (channel.getMetadata() != null && channel.getMetadata().get("shopDomain") != null) {
            return channel.getMetadata().get("shopDomain").toString();
        }
        return channel.getDisplayName();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> nestedMap(Map<String, Object> source, String firstKey, String secondKey) {
        return map(map(source.get(firstKey)).get(secondKey));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> source ? (Map<String, Object>) source : new HashMap<>();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> list(Map<String, Object> source, String key) {
        Object value = source.get(key);
        return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    private void ensureNoGraphQlErrors(Map<String, Object> response) {
        if (response.get("errors") != null) {
            throw new IllegalStateException("Shopify GraphQL lỗi: " + response.get("errors"));
        }
    }

    private String numericId(String gid) {
        if (gid == null) {
            return UUID.randomUUID().toString();
        }
        int index = gid.lastIndexOf('/');
        return index >= 0 ? gid.substring(index + 1) : gid;
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

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? 0 : new BigDecimal(value.toString()).intValue();
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && Boolean.parseBoolean(value.toString());
    }

    private BigDecimal decimalValue(Object value) {
        try {
            return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private Map<String, Object> mapOf(String key, Object value) {
        Map<String, Object> map = new HashMap<>();
        map.put(key, value);
        return map;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String toSlug(String value) {
        String normalized = Normalizer.normalize(value == null ? "category" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return normalized.isBlank() ? "category" : normalized;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String withLocationIdMarker(String address, String locationId) {
        String marker = "[" + LOCATION_ID_MARKER + locationId + "]";
        if (address == null || address.isBlank()) {
            return marker;
        }
        int markerIndex = address.indexOf("[" + LOCATION_ID_MARKER);
        if (markerIndex >= 0) {
            return address.substring(0, markerIndex).trim() + " " + marker;
        }
        return address.trim() + " " + marker;
    }
}
