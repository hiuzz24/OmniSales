package fu.osms.sync.tiktok.impl;

import fu.osms.catalog.entity.Product;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.catalog.enums.ProductStatus;
import fu.osms.catalog.repository.ProductRepository;
import fu.osms.catalog.repository.ProductVariantRepository;
import fu.osms.channel.dto.response.ChannelImportSyncResponse;
import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelProduct;
import fu.osms.channel.entity.ChannelProductVariant;
import fu.osms.channel.repository.ChannelProductRepository;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.enums.SyncStatus;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.inventory.entity.InventoryItem;
import fu.osms.inventory.entity.Warehouse;
import fu.osms.inventory.repository.InventoryItemRepository;
import fu.osms.inventory.repository.WarehouseRepository;
import fu.osms.sync.entity.SyncLog;
import fu.osms.sync.repository.SyncLogRepository;
import fu.osms.sync.service.MarketplaceInventoryPropagationService;
import fu.osms.sync.service.MarketplaceWarehouseConsistencyService;
import fu.osms.sync.service.impl.ChannelProductAggregationService;
import fu.osms.sync.tiktok.TikTokAuthorizedApiClient;
import fu.osms.sync.tiktok.TikTokImportSyncService;
import fu.osms.sync.tiktok.util.TikTokWarehouseAddressFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TikTokImportSyncServiceImpl implements TikTokImportSyncService {

    private static final int INVENTORY_BATCH_SIZE = 100;
    private static final String WAREHOUSE_MARKER = "TIKTOK_WAREHOUSE_ID:";

    private final ChannelRepository channelRepository;
    private final ChannelProductRepository channelProductRepository;
    private final ChannelProductVariantRepository channelProductVariantRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final WarehouseRepository warehouseRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final SyncLogRepository syncLogRepository;
    private final TikTokAuthorizedApiClient tikTokApiClient;
    private final MarketplaceInventoryPropagationService marketplaceInventoryPropagationService;
    private final MarketplaceWarehouseConsistencyService marketplaceWarehouseConsistencyService;
    private final ChannelProductAggregationService channelProductAggregationService;

    @Override
    @Transactional
    public ChannelImportSyncResponse syncProductsAndInventory(UUID channelId) {
        Channel channel = requireTikTokChannel(channelId);
        String shopCipher = requireShopCipher(channel);
        marketplaceWarehouseConsistencyService.resolveAndValidatePrimaryWarehouse(channel);

        SyncLog syncLog = syncLogRepository.save(SyncLog.builder()
                .channel(channel)
                .jobType("TIKTOK_REMOTE_IMPORT_SYNC")
                .status(SyncStatus.PENDING)
                .startedAt(OffsetDateTime.now())
                .build());

        int productCount = 0;
        int variantCount = 0;
        Set<String> externalWarehouseIds = new LinkedHashSet<>();

        try {
            List<Map<String, Object>> productSummaries = loadAllProductSummaries(channelId, shopCipher);
            Map<String, Map<String, Object>> inventoryByProductId = loadInventoryByProductId(
                    channelId,
                    shopCipher,
                    productSummaries.stream().map(this::productId).filter(this::hasText).toList()
            );
            Map<String, Map<String, Object>> warehousesById = loadWarehousesById(channelId, shopCipher);
            Map<String, Warehouse> localWarehousesByExternalId = syncTikTokWarehouses(warehousesById);
            externalWarehouseIds.addAll(localWarehousesByExternalId.keySet());

            UUID defaultWarehouseId = resolveDefaultWarehouseId(channel);
            boolean loadFullProductDetail = shouldLoadFullProductDetail(channel);
            Set<UUID> changedVariantIds = new LinkedHashSet<>();
            for (Map<String, Object> summary : productSummaries) {
                String externalProductId = productId(summary);
                if (!hasText(externalProductId)) {
                    continue;
                }

                Map<String, Object> detail = loadFullProductDetail || !hasText(productTitle(summary))
                        ? loadProductDetail(channelId, shopCipher, externalProductId, summary)
                        : summary;
                Map<String, Object> inventory = inventoryByProductId.getOrDefault(externalProductId, Map.of());
                ImportedProduct imported = upsertProduct(
                        channel,
                        detail,
                        inventory,
                        defaultWarehouseId,
                        externalWarehouseIds,
                        warehousesById,
                        localWarehousesByExternalId
                );
                productCount++;
                variantCount += imported.variantCount();
                changedVariantIds.addAll(imported.variantIds());
            }

            Map<String, Object> metadata = mutableMap(channel.getMetadata());
            metadata.put("productCount", channelProductRepository.countByChannelIdAndMappingState(channelId, "ACTIVE"));
            metadata.put("skuVariantCount", channelProductVariantRepository.countActiveByChannelId(channelId));
            metadata.put("warehouseCount", externalWarehouseIds.size());
            channel.setMetadata(metadata);
            channel.setLastSyncedAt(OffsetDateTime.now());
            channelRepository.save(channel);

            completeLog(syncLog, SyncStatus.SYNCED, productCount + variantCount, productCount + variantCount, 0, null);
            marketplaceInventoryPropagationService.schedulePushAvailableStock(changedVariantIds);
            return ChannelImportSyncResponse.builder()
                    .channelId(channelId)
                    .syncLogId(syncLog.getId())
                    .productCount(productCount)
                    .variantCount(variantCount)
                    .warehouseCount(externalWarehouseIds.size())
                    .pushedVariantCount(0)
                    .status(SyncStatus.SYNCED.name())
                    .message("Đã đồng bộ sản phẩm và tồn kho từ TikTok Shop.")
                    .build();
        } catch (Exception e) {
            completeLog(syncLog, SyncStatus.FAILED, productCount + variantCount, productCount + variantCount, 1, e.getMessage());
            throw e;
        }
    }

    private List<Map<String, Object>> loadAllProductSummaries(UUID channelId, String shopCipher) {
        List<Map<String, Object>> products = new ArrayList<>();
        String pageToken = null;
        do {
            Map<String, Object> response = tikTokApiClient.searchProducts(channelId, shopCipher, pageToken);
            log.info("[TikTokImportSync] Product search response pageToken={}, payload={}",
                    pageToken, response);
            Map<String, Object> data = map(response.get("data"));
            products.addAll(listOfMaps(data.get("products")));
            pageToken = stringValue(data.get("next_page_token"));
        } while (hasText(pageToken));
        return products;
    }

    private Map<String, Map<String, Object>> loadInventoryByProductId(UUID channelId,
                                                                      String shopCipher,
                                                                      List<String> productIds) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (int from = 0; from < productIds.size(); from += INVENTORY_BATCH_SIZE) {
            List<String> batch = productIds.subList(from, Math.min(productIds.size(), from + INVENTORY_BATCH_SIZE));
            Map<String, Object> response = tikTokApiClient.searchInventory(channelId, shopCipher, batch);
            log.info("[TikTokImportSync] Inventory response productIds={}, payload={}", batch, response);
            for (Map<String, Object> inventory : listOfMaps(map(response.get("data")).get("inventory"))) {
                String productId = stringValue(inventory.get("product_id"));
                if (hasText(productId)) {
                    result.put(productId, inventory);
                }
            }
        }
        return result;
    }

    private Map<String, Map<String, Object>> loadWarehousesById(UUID channelId, String shopCipher) {
        Map<String, Object> response = tikTokApiClient.getWarehouses(channelId, shopCipher);
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map<String, Object> warehouse : listOfMaps(map(response.get("data")).get("warehouses"))) {
            String warehouseId = stringValue(warehouse.get("id"));
            if (hasText(warehouseId)) {
                result.put(warehouseId, warehouse);
            }
        }
        return result;
    }

    private Map<String, Warehouse> syncTikTokWarehouses(Map<String, Map<String, Object>> warehousesById) {
        Map<String, Warehouse> result = new LinkedHashMap<>();
        warehousesById.forEach((warehouseId, warehouseNode) -> {
            if (isSalesWarehouse(warehouseNode)) {
                result.put(warehouseId, resolveTikTokWarehouse(warehouseId, warehouseNode));
            } else {
                deactivateTikTokWarehouse(warehouseId);
            }
        });
        return result;
    }

    private Map<String, Object> loadProductDetail(UUID channelId,
                                                   String shopCipher,
                                                   String externalProductId,
                                                   Map<String, Object> fallback) {
        Map<String, Object> response = tikTokApiClient.getProduct(channelId, shopCipher, externalProductId);
        log.info("[TikTokImportSync] Product detail response productId={}, payload={}",
                externalProductId, response);
        Map<String, Object> data = map(response.get("data"));
        Map<String, Object> product = map(data.get("product"));
        return product.isEmpty() ? (data.isEmpty() ? fallback : data) : product;
    }

    private boolean shouldLoadFullProductDetail(Channel channel) {
        Object value = channel.getMetadata() == null ? null : channel.getMetadata().get("syncFullProductDetail");
        return value instanceof Boolean bool ? bool : value != null && Boolean.parseBoolean(value.toString());
    }

    private ImportedProduct upsertProduct(Channel channel,
                                          Map<String, Object> productNode,
                                          Map<String, Object> inventoryNode,
                                          UUID defaultWarehouseId,
                                          Set<String> externalWarehouseIds,
                                          Map<String, Map<String, Object>> warehousesById,
                                          Map<String, Warehouse> localWarehousesByExternalId) {
        String externalProductId = firstNonBlank(productId(productNode), productId(inventoryNode));
        ChannelProduct channelProduct = channelProductRepository
                .findByChannelIdAndExternalProductId(channel.getId(), externalProductId)
                .orElseGet(ChannelProduct::new);

        List<Map<String, Object>> inventorySkus = listOfMaps(inventoryNode.get("skus"));
        Product product = findProductBySharedSku(inventorySkus)
                .orElse(channelProduct.getProduct());
        if (product == null) {
            String productName = firstNonBlank(productTitle(productNode), "TikTok Product " + externalProductId);
            product = java.util.Optional.<Product>empty()
                    .or(() -> productRepository.findFirstByNameIgnoreCaseAndDeletedAtIsNullOrderByCreatedAtAsc(productName))
                    .orElseGet(() -> productRepository.findFirstBySkuAndDeletedAtIsNull(fallbackSku(externalProductId))
                            .orElseGet(Product::new));
        }
        boolean tikTokOwnedProduct = product.getId() == null || isGeneratedPlatformSku(stringValue(product.getSku()));
        if (product.getId() == null) {
            product.setSku(firstSellerSku(inventorySkus, fallbackSku(externalProductId)));
        }
        if (tikTokOwnedProduct) {
            product.setName(firstNonBlank(productTitle(productNode), "TikTok Product " + externalProductId));
            product.setDescription(firstNonBlank(
                    stringValue(productNode.get("description")),
                    stringValue(productNode.get("description_html"))
            ));
            product.setBrand(firstNonBlank(stringValue(productNode.get("brand_name")), product.getBrand()));
            product.setStatus(resolveStatus(stringValue(productNode.get("status"))));
        }
        product.setUnit(firstNonBlank(product.getUnit(), "pcs"));
        product.setLowStockThreshold(product.getLowStockThreshold() == null ? 5 : product.getLowStockThreshold());
        Map<String, Object> attributes = mutableMap(product.getAttributes());
        attributes.put("tiktokProductId", externalProductId);
        product.setAttributes(attributes);
        product = productRepository.save(product);

        channelProduct.setChannel(channel);
        channelProduct.setProduct(product);
        channelProduct.setExternalProductId(externalProductId);
        channelProduct.setExternalStatus(stringValue(productNode.get("status")));
        channelProduct.setMappingState("ACTIVE");
        channelProduct.setSyncStatus(SyncStatus.SYNCED);
        channelProduct.setLastSyncedAt(OffsetDateTime.now());
        channelProduct.setLastSyncError(null);
        channelProduct = channelProductRepository.save(channelProduct);
        channelProduct = channelProductAggregationService.normalizeImportedMapping(channelProduct);

        Map<String, Map<String, Object>> detailsBySkuId = indexById(listOfMaps(productNode.get("skus")));
        int variantCount = 0;
        Set<UUID> variantIds = new LinkedHashSet<>();
        for (Map<String, Object> skuInventory : inventorySkus) {
            String externalSkuId = stringValue(skuInventory.get("id"));
            if (!hasText(externalSkuId)) {
                continue;
            }
            Map<String, Object> skuDetail = detailsBySkuId.getOrDefault(externalSkuId, Map.of());
            ProductVariant variant = upsertVariant(channelProduct, product, skuInventory, skuDetail);
            upsertChannelVariant(channelProduct, variant, skuInventory, skuDetail);
            upsertInventory(
                    variant,
                    skuInventory,
                    defaultWarehouseId,
                    externalWarehouseIds,
                    warehousesById,
                    localWarehousesByExternalId
            );
            variantIds.add(variant.getId());
            variantCount++;
        }
        return new ImportedProduct(variantCount, variantIds);
    }

    private java.util.Optional<Product> findProductBySharedSku(List<Map<String, Object>> skuNodes) {
        return skuNodes.stream()
                .map(node -> stringValue(node.get("seller_sku")))
                .filter(this::hasText)
                .map(productVariantRepository::findBySkuAndDeletedAtIsNull)
                .flatMap(java.util.Optional::stream)
                .map(ProductVariant::getProduct)
                .filter(java.util.Objects::nonNull)
                .findFirst();
    }

    private ProductVariant upsertVariant(ChannelProduct channelProduct,
                                         Product product,
                                         Map<String, Object> inventorySku,
                                         Map<String, Object> detailSku) {
        String externalSkuId = stringValue(inventorySku.get("id"));
        String sellerSku = firstNonBlank(
                stringValue(inventorySku.get("seller_sku")),
                stringValue(detailSku.get("seller_sku")),
                fallbackSku(externalSkuId)
        );
        ProductVariant variant = productVariantRepository.findBySkuAndDeletedAtIsNull(sellerSku)
                .filter(existing -> sameProduct(existing, product))
                .or(() -> channelProductVariantRepository
                        .findByChannelProductIdAndExternalVariantId(channelProduct.getId(), externalSkuId)
                        .map(ChannelProductVariant::getVariant))
                .orElseGet(ProductVariant::new);
        if (variant.getId() == null) {
            variant.setProduct(product);
            variant.setSku(uniqueSku(sellerSku, externalSkuId));
            variant.setName(firstNonBlank(
                    stringValue(detailSku.get("seller_sku")),
                    stringValue(detailSku.get("title")),
                    product.getName()
            ));
            variant.setOptionValues(Map.of("source", "TikTok Shop"));
        }
        BigDecimal externalPrice = moneyAmount(detailSku.get("price"));
        if (variant.getPrice() == null && externalPrice != null) {
            variant.setPrice(externalPrice);
        }
        variant.setIsActive(true);
        return productVariantRepository.save(variant);
    }

    private void upsertChannelVariant(ChannelProduct channelProduct,
                                      ProductVariant variant,
                                      Map<String, Object> inventorySku,
                                      Map<String, Object> detailSku) {
        String externalSkuId = stringValue(inventorySku.get("id"));
        ChannelProductVariant mapping = resolveChannelVariantMapping(channelProduct, variant, externalSkuId);
        mapping.setChannelProduct(channelProduct);
        mapping.setVariant(variant);
        applyExternalVariantId(channelProduct, mapping, externalSkuId);
        mapping.setExternalSku(firstNonBlank(stringValue(inventorySku.get("seller_sku")), variant.getSku()));
        mapping.setExternalPrice(moneyAmount(detailSku.get("price")));
        mapping.setSyncStatus(SyncStatus.SYNCED);
        mapping.setLastSyncedAt(OffsetDateTime.now());
        Map<String, Object> metadata = mutableMap(mapping.getMetadata());
        List<String> warehouseIds = listOfMaps(inventorySku.get("warehouse_inventory")).stream()
                .map(node -> stringValue(node.get("warehouse_id")))
                .filter(this::hasText)
                .distinct()
                .toList();
        metadata.put("tiktokWarehouseIds", warehouseIds);
        metadata.put("tiktokAvailableQuantity", intValue(inventorySku.get("total_available_quantity")));
        mapping.setMetadata(metadata);
        channelProductVariantRepository.save(mapping);
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
                                        ChannelProductVariant mapping,
                                        String externalVariantId) {
        channelProductVariantRepository
                .findByChannelProductIdAndExternalVariantId(channelProduct.getId(), externalVariantId)
                .filter(existing -> mapping.getId() != null && !Objects.equals(existing.getId(), mapping.getId()))
                .ifPresentOrElse(
                        existing -> {
                            Map<String, Object> metadata = mutableMap(mapping.getMetadata());
                            metadata.put("conflictingTikTokExternalVariantId", externalVariantId);
                            metadata.put("conflictingChannelProductVariantId", existing.getId().toString());
                            mapping.setMetadata(metadata);
                        },
                        () -> mapping.setExternalVariantId(externalVariantId)
                );
    }

    private void upsertInventory(ProductVariant variant,
                                 Map<String, Object> inventorySku,
                                 UUID defaultWarehouseId,
                                 Set<String> externalWarehouseIds,
                                 Map<String, Map<String, Object>> warehousesById,
                                 Map<String, Warehouse> localWarehousesByExternalId) {
        List<Map<String, Object>> warehouseInventory = listOfMaps(inventorySku.get("warehouse_inventory"));
        warehouseInventory.stream()
                .map(node -> stringValue(node.get("warehouse_id")))
                .filter(this::hasText)
                .filter(warehouseId -> isSalesWarehouse(warehousesById.getOrDefault(warehouseId, Map.of())))
                .forEach(externalWarehouseIds::add);

        if (defaultWarehouseId != null) {
            Warehouse warehouse = warehouseRepository.findById(defaultWarehouseId)
                    .filter(item -> item.getDeletedAt() == null && Boolean.TRUE.equals(item.getIsActive()))
                    .orElseThrow(() -> new AppException(ErrorCode.WAREHOUSE_NOT_FOUND));
            int availableQuantity = intValue(inventorySku.get("total_available_quantity"));
            int committedQuantity = intValue(inventorySku.get("total_committed_quantity"));
            for (Map<String, Object> warehouseNode : warehouseInventory) {
                int candidateAvailable = intValue(warehouseNode.get("available_quantity"));
                int candidateCommitted = intValue(warehouseNode.get("committed_quantity"));
                if (candidateAvailable + candidateCommitted > availableQuantity + committedQuantity) {
                    availableQuantity = candidateAvailable;
                    committedQuantity = candidateCommitted;
                }
            }
            saveInventoryItem(
                    warehouse,
                    variant,
                    availableQuantity,
                    committedQuantity
            );
            return;
        }

        for (Map<String, Object> warehouseNode : warehouseInventory) {
            String externalWarehouseId = stringValue(warehouseNode.get("warehouse_id"));
            if (!hasText(externalWarehouseId)) {
                continue;
            }
            Map<String, Object> tikTokWarehouse = warehousesById.getOrDefault(externalWarehouseId, Map.of());
            if (!isSalesWarehouse(tikTokWarehouse)) {
                continue;
            }
            Warehouse warehouse = localWarehousesByExternalId.computeIfAbsent(
                    externalWarehouseId,
                    warehouseId -> resolveTikTokWarehouse(warehouseId, tikTokWarehouse)
            );
            saveInventoryItem(
                    warehouse,
                    variant,
                    intValue(warehouseNode.get("available_quantity")),
                    intValue(warehouseNode.get("committed_quantity"))
            );
        }
    }

    private void saveInventoryItem(Warehouse warehouse,
                                   ProductVariant variant,
                                   int availableQuantity,
                                   int committedQuantity) {
        InventoryItem item = inventoryItemRepository
                .findByWarehouseIdAndVariantId(warehouse.getId(), variant.getId())
                .orElseGet(InventoryItem::new);
        item.setWarehouse(warehouse);
        item.setVariant(variant);
        item.setQuantityOnHand(Math.max(0, availableQuantity) + Math.max(0, committedQuantity));
        item.setReservedQuantity(Math.max(0, committedQuantity));
        item.setLowStockThreshold(item.getLowStockThreshold() == null ? 5 : item.getLowStockThreshold());
        item.setAverageCost(item.getAverageCost() == null ? BigDecimal.ZERO : item.getAverageCost());
        inventoryItemRepository.save(item);
    }

    private Warehouse resolveTikTokWarehouse(String externalWarehouseId, Map<String, Object> warehouseNode) {
        String marker = "[" + WAREHOUSE_MARKER + externalWarehouseId + "]";
        Warehouse warehouse = findTikTokWarehouse(externalWarehouseId).orElseGet(Warehouse::new);
        warehouse.setName("TikTok Shop - "
                + firstNonBlank(stringValue(warehouseNode.get("name")), "Warehouse")
                + " " + marker);
        warehouse.setAddress(TikTokWarehouseAddressFormatter.format(
                map(warehouseNode.get("address")),
                externalWarehouseId
        ));
        warehouse.setIsActive(!"DISABLED".equalsIgnoreCase(stringValue(warehouseNode.get("effect_status"))));
        warehouse.setDeletedAt(null);
        return warehouseRepository.save(warehouse);
    }

    private void deactivateTikTokWarehouse(String externalWarehouseId) {
        findTikTokWarehouse(externalWarehouseId).ifPresent(warehouse -> {
            warehouse.setIsActive(false);
            warehouseRepository.save(warehouse);
        });
    }

    private java.util.Optional<Warehouse> findTikTokWarehouse(String externalWarehouseId) {
        String marker = "[" + WAREHOUSE_MARKER + externalWarehouseId + "]";
        return warehouseRepository.findByDeletedAtIsNull().stream()
                .filter(item -> containsMarker(item.getName(), marker) || containsMarker(item.getAddress(), marker))
                .findFirst();
    }

    private boolean isSalesWarehouse(Map<String, Object> warehouseNode) {
        return "SALES_WAREHOUSE".equalsIgnoreCase(stringValue(warehouseNode.get("type")));
    }

    private boolean containsMarker(String value, String marker) {
        return value != null && value.contains(marker);
    }

    private Channel requireTikTokChannel(UUID channelId) {
        Channel channel = channelRepository.findById(channelId)
                .filter(item -> item.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.CHANNEL_NOT_FOUND));
        if (channel.getPlatform() != PlatformType.TIKTOK) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Kênh không phải TikTok Shop.");
        }
        return channel;
    }

    private String requireShopCipher(Channel channel) {
        Map<String, Object> metadata = mutableMap(channel.getMetadata());
        String shopCipher = firstNonBlank(
                stringValue(metadata.get("shopCipher")),
                stringValue(metadata.get("shop_cipher")),
                stringValue(metadata.get("cipher"))
        );
        if (!hasText(shopCipher)) {
            throw new AppException(
                    ErrorCode.INVALID_REQUEST,
                    "Kênh TikTok thiếu shopCipher. Hãy nhập Shop Cipher từ API Get Authorized Shops trong cấu hình kênh."
            );
        }
        return shopCipher;
    }

    private UUID resolveDefaultWarehouseId(Channel channel) {
        String value = stringValue(mutableMap(channel.getMetadata()).get("defaultWarehouseId"));
        if (!hasText(value)) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "defaultWarehouseId của kênh TikTok không hợp lệ.");
        }
    }

    private void completeLog(SyncLog log,
                             SyncStatus status,
                             int total,
                             int success,
                             int failed,
                             String error) {
        log.setStatus(status);
        log.setTotalItems(total);
        log.setSuccessCount(success);
        log.setFailCount(failed);
        log.setErrorSummary(error);
        log.setCompletedAt(OffsetDateTime.now());
        syncLogRepository.save(log);
    }

    private String uniqueSku(String baseSku, String externalSkuId) {
        return productVariantRepository.findBySkuAndDeletedAtIsNull(baseSku)
                .map(existing -> baseSku + "-" + externalSkuId)
                .orElse(baseSku);
    }

    private String firstSellerSku(List<Map<String, Object>> skuNodes, String fallback) {
        return skuNodes.stream()
                .map(node -> stringValue(node.get("seller_sku")))
                .filter(this::hasText)
                .findFirst()
                .orElse(fallback);
    }

    private String fallbackSku(String externalId) {
        return "EXT-" + firstNonBlank(externalId, UUID.randomUUID().toString());
    }

    private boolean isGeneratedPlatformSku(String sku) {
        return sku != null && (sku.startsWith("SHOPIFY-") || sku.startsWith("LAZADA-") || sku.startsWith("TIKTOK-"));
    }

    private boolean sameProduct(ProductVariant variant, Product product) {
        return variant.getProduct() != null
                && variant.getProduct().getId() != null
                && product.getId() != null
                && variant.getProduct().getId().equals(product.getId());
    }

    private Map<String, Map<String, Object>> indexById(List<Map<String, Object>> nodes) {
        Map<String, Map<String, Object>> result = new HashMap<>();
        for (Map<String, Object> node : nodes) {
            String id = stringValue(node.get("id"));
            if (hasText(id)) {
                result.put(id, node);
            }
        }
        return result;
    }

    private String productId(Map<String, Object> node) {
        return firstNonBlank(stringValue(node.get("id")), stringValue(node.get("product_id")));
    }

    private String productTitle(Map<String, Object> node) {
        return firstNonBlank(
                stringValue(node.get("title")),
                stringValue(node.get("name")),
                stringValue(node.get("product_name"))
        );
    }

    private ProductStatus resolveStatus(String status) {
        return "ACTIVATE".equalsIgnoreCase(status) ? ProductStatus.ACTIVE : ProductStatus.INACTIVE;
    }

    private BigDecimal moneyAmount(Object value) {
        Object amount = value instanceof Map<?, ?> map ? map.get("amount") : value;
        if (amount == null) {
            return null;
        }
        try {
            return new BigDecimal(amount.toString().replaceAll("[^0-9.-]", ""));
        } catch (NumberFormatException e) {
            return null;
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

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? 0 : Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
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

    private record ImportedProduct(int variantCount, Set<UUID> variantIds) {
    }
}
