package fu.osms.sync.tiktok.impl;

import fu.osms.catalog.entity.Product;
import fu.osms.catalog.entity.Category;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.catalog.enums.CategoryStatus;
import fu.osms.catalog.enums.ProductStatus;
import fu.osms.catalog.repository.CategoryRepository;
import fu.osms.catalog.repository.ProductRepository;
import fu.osms.catalog.repository.ProductVariantRepository;
import fu.osms.catalog.util.ProductCostPolicy;
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
import fu.osms.sync.service.PlatformCatalogOwnershipPolicy;
import fu.osms.sync.service.impl.ChannelProductAggregationService;
import fu.osms.sync.service.impl.SyncJobProgressTracker;
import fu.osms.sync.tiktok.TikTokAuthorizedApiClient;
import fu.osms.sync.tiktok.TikTokImportSyncService;
import fu.osms.sync.tiktok.TikTokProductDetailEnrichmentService;
import fu.osms.sync.tiktok.util.TikTokWarehouseAddressFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
@Slf4j
public class TikTokImportSyncServiceImpl implements TikTokImportSyncService {

    private static final int INVENTORY_BATCH_SIZE = 100;
    private static final int MAX_VARIANT_SKU_LENGTH = 100;
    private static final String WAREHOUSE_MARKER = "TIKTOK_WAREHOUSE_ID:";

    private final ChannelRepository channelRepository;
    private final ChannelProductRepository channelProductRepository;
    private final ChannelProductVariantRepository channelProductVariantRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductVariantRepository productVariantRepository;
    private final WarehouseRepository warehouseRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final SyncLogRepository syncLogRepository;
    private final TikTokAuthorizedApiClient tikTokApiClient;
    private final MarketplaceInventoryPropagationService marketplaceInventoryPropagationService;
    private final MarketplaceWarehouseConsistencyService marketplaceWarehouseConsistencyService;
    private final ChannelProductAggregationService channelProductAggregationService;
    private final SyncJobProgressTracker syncJobProgressTracker;
    private final TikTokProductDetailEnrichmentService tikTokProductDetailEnrichmentService;
    private final PlatformCatalogOwnershipPolicy catalogOwnershipPolicy;

    @Override
    @Transactional
    public ChannelImportSyncResponse syncProductsAndInventory(UUID channelId) {
        Channel channel = requireTikTokChannel(channelId);
        String shopCipher = requireShopCipher(channel);
        Warehouse masterWarehouse = marketplaceWarehouseConsistencyService.resolveAndValidatePrimaryWarehouse(channel);

        SyncLog syncLog = syncLogRepository.save(SyncLog.builder()
                .channel(channel)
                .jobType("TIKTOK_REMOTE_IMPORT_SYNC")
                .status(SyncStatus.PENDING)
                .startedAt(OffsetDateTime.now())
                .build());

        int productCount = 0;
        int variantCount = 0;

        try {
            Map<String, Map<String, Object>> warehousesById = loadWarehousesById(channelId, shopCipher);
            Map<String, Warehouse> localWarehousesByExternalId = syncTikTokWarehouses(warehousesById);
            Set<String> externalWarehouseIds = new LinkedHashSet<>(localWarehousesByExternalId.keySet());

            UUID defaultWarehouseId = resolveDefaultWarehouseId(channel);
            boolean loadFullProductDetail = shouldLoadFullProductDetail(channel);
            Set<UUID> changedVariantIds = new LinkedHashSet<>();
            Set<UUID> pendingEnrichmentIds = new LinkedHashSet<>();
            int[] importedCounts = {0, 0};
            OffsetDateTime changedSince = channel.getLastSyncedAt();
            forEachProductSummaryPage(channelId, shopCipher, changedSince, productSummaries -> {
                Map<String, Map<String, Object>> inventoryByProductId = loadInventoryByProductId(
                        channelId,
                        shopCipher,
                        productSummaries.stream().map(this::productId).filter(this::hasText).toList()
                );
                int pageVariantCount = 0;
                for (Map<String, Object> summary : productSummaries) {
                    String externalProductId = productId(summary);
                    if (!hasText(externalProductId)) {
                        continue;
                    }

                    boolean detailLoaded = loadFullProductDetail || !hasText(productTitle(summary));
                    Map<String, Object> detail = detailLoaded
                            ? loadProductDetail(channelId, shopCipher, externalProductId, summary)
                            : summary;
                    Map<String, Object> inventory = inventoryByProductId.getOrDefault(externalProductId, Map.of());
                    if (listOfMaps(inventory.get("skus")).isEmpty() && listOfMaps(detail.get("skus")).isEmpty()) {
                        detail = loadProductDetail(channelId, shopCipher, externalProductId, summary);
                        detailLoaded = true;
                    }
                    ImportedProduct imported = upsertProduct(
                            channel,
                            detail,
                            inventory,
                            masterWarehouse,
                            defaultWarehouseId,
                            externalWarehouseIds,
                            warehousesById,
                            localWarehousesByExternalId,
                            syncLog
                    );
                    importedCounts[0]++;
                    importedCounts[1] += imported.variantCount();
                    pageVariantCount += imported.variantCount();
                    changedVariantIds.addAll(imported.variantIds());
                    if (!detailLoaded) {
                        pendingEnrichmentIds.add(imported.channelProductId());
                    }
                }
                syncJobProgressTracker.recordVariantBatch(pageVariantCount);
            });
            productCount = importedCounts[0];
            variantCount = importedCounts[1];

            Map<String, Object> metadata = mutableMap(channel.getMetadata());
            metadata.put("productCount", channelProductRepository.countByChannelIdAndMappingState(channelId, "ACTIVE"));
            metadata.put("skuVariantCount", channelProductVariantRepository.countActiveByChannelId(channelId));
            metadata.put("warehouseCount", externalWarehouseIds.size());
            channel.setMetadata(metadata);
            channel.setLastSyncedAt(OffsetDateTime.now());
            channelRepository.save(channel);

            completeLog(syncLog, SyncStatus.SYNCED, productCount + variantCount, productCount + variantCount, 0, null);
            marketplaceInventoryPropagationService.schedulePushAvailableStock(changedVariantIds, channelId);
            scheduleDetailEnrichmentAfterCommit(channelId, pendingEnrichmentIds);
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

    private void scheduleDetailEnrichmentAfterCommit(UUID channelId, Collection<UUID> channelProductIds) {
        if (channelProductIds == null || channelProductIds.isEmpty()) {
            return;
        }
        List<UUID> enrichmentIds = List.copyOf(channelProductIds);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            tikTokProductDetailEnrichmentService.enrichChannelProducts(channelId, enrichmentIds);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                tikTokProductDetailEnrichmentService.enrichChannelProducts(channelId, enrichmentIds);
            }
        });
    }

    private void forEachProductSummaryPage(UUID channelId,
                                           String shopCipher,
                                           OffsetDateTime changedSince,
                                           Consumer<List<Map<String, Object>>> pageConsumer) {
        String pageToken = null;
        do {
            log.info("[TikTokImportSync] Calling product search channelId={}, pageToken={}, update_time_ge={}",
                    channelId,
                    hasText(pageToken) ? "<present>" : "<none>",
                    changedSince == null ? null : changedSince.toEpochSecond());
            Map<String, Object> response = tikTokApiClient.searchProducts(channelId, shopCipher, pageToken, changedSince);
            Map<String, Object> data = map(response.get("data"));
            List<Map<String, Object>> products = listOfMaps(data.get("products"));
            log.info("[TikTokImportSync] Product search page channelId={}, received={}, nextPageToken={}",
                    channelId,
                    products.size(),
                    hasText(stringValue(data.get("next_page_token"))) ? "<present>" : "<none>");
            if (!products.isEmpty()) {
                pageConsumer.accept(products);
            }
            pageToken = stringValue(data.get("next_page_token"));
        } while (hasText(pageToken));
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
                                          Warehouse masterWarehouse,
                                          UUID defaultWarehouseId,
                                          Set<String> externalWarehouseIds,
                                          Map<String, Map<String, Object>> warehousesById,
                                          Map<String, Warehouse> localWarehousesByExternalId,
                                          SyncLog syncLog) {
        String externalProductId = firstNonBlank(productId(productNode), productId(inventoryNode));
        ChannelProduct channelProduct = channelProductRepository
                .findByChannelIdAndExternalProductId(channel.getId(), externalProductId)
                .orElseGet(ChannelProduct::new);

        List<Map<String, Object>> inventorySkus = listOfMaps(inventoryNode.get("skus"));
        List<Map<String, Object>> detailSkus = listOfMaps(productNode.get("skus"));
        Product product = channelProduct.getProduct();
        boolean initialCreate = product == null;
        if (initialCreate) {
            product = Product.builder()
                    .sku(fallbackSku(externalProductId))
                    .attributes(new LinkedHashMap<>())
                    .build();
        }
        boolean tikTokOwnedProduct = initialCreate
                || catalogOwnershipPolicy.isPlatformOwned(channelProduct, product, externalProductId);
        if (initialCreate) {
            product.setSku(fallbackSku(externalProductId));
        }
        if (tikTokOwnedProduct) {
            product.setName(firstNonBlank(productTitle(productNode), "TikTok Product " + externalProductId));
            product.setDescription(firstNonBlank(
                    stringValue(productNode.get("description")),
                    stringValue(productNode.get("description_html"))
            ));
            product.setBrand(firstNonBlank(stringValue(productNode.get("brand_name")), product.getBrand()));
            try {
                Category resolvedCategory = resolveCategory(productNode);
                if (resolvedCategory != null) {
                    product.setCategory(resolvedCategory);
                }
            } catch (Exception e) {
                log.warn("[TikTokImportSync] Could not resolve category for product {}: {}",
                        externalProductId, e.getMessage());
            }
        }
        if (initialCreate) {
            product.setStatus(resolveStatus(stringValue(productNode.get("status"))));
            product.setUnit("pcs");
            product.setLowStockThreshold(5);
        }
        product = productRepository.save(product);

        channelProduct.setChannel(channel);
        channelProduct.setProduct(product);
        channelProduct.setExternalProductId(externalProductId);
        channelProduct.setExternalStatus(stringValue(productNode.get("status")));
        channelProduct.setMappingState("ACTIVE");
        channelProduct.setSyncStatus(SyncStatus.SYNCED);
        channelProduct.setLastSyncedAt(OffsetDateTime.now());
        channelProduct.setLastSyncError(null);
        if (initialCreate) {
            catalogOwnershipPolicy.markPlatformImported(channelProduct);
        }
        channelProduct = channelProductRepository.save(channelProduct);
        channelProduct = channelProductAggregationService.normalizeImportedMapping(channelProduct);

        Map<String, Map<String, Object>> detailsBySkuId = indexById(detailSkus);
        List<Map<String, Object>> skuSources = inventorySkus.isEmpty() ? detailSkus : inventorySkus;
        int variantCount = 0;
        Set<UUID> variantIds = new LinkedHashSet<>();
        for (Map<String, Object> skuSource : skuSources) {
            String externalSkuId = stringValue(skuSource.get("id"));
            if (!hasText(externalSkuId)) {
                continue;
            }
            Map<String, Object> skuDetail = detailsBySkuId.getOrDefault(externalSkuId, skuSource);
            Map<String, Object> skuInventory = inventorySkus.isEmpty() ? skuSource : skuSource;
            ProductVariant variant = upsertVariant(
                    channelProduct, product, skuInventory, skuDetail);
            if (variant == null) {
                String warning = "Remote TikTok variant is not linked to an OSMS variant: "
                        + firstNonBlank(
                                stringValue(skuInventory.get("seller_sku")),
                                stringValue(skuDetail.get("seller_sku")),
                                externalSkuId
                        );
                log.warn(
                        "[TikTokImportSync] Skip unmapped remote variant for existing product channelId={} "
                                + "externalProductId={} externalVariantId={} sellerSku={}",
                        channel.getId(),
                        externalProductId,
                        externalSkuId,
                        firstNonBlank(
                                stringValue(skuInventory.get("seller_sku")),
                                stringValue(skuDetail.get("seller_sku"))
                        )
                );
                appendSyncWarning(syncLog, warning);
                continue;
            }
            upsertChannelVariant(channelProduct, variant, skuInventory, skuDetail);
            if (initialCreate) {
                upsertInventory(
                        variant,
                        skuInventory,
                        defaultWarehouseId,
                        masterWarehouse,
                        externalWarehouseIds,
                        warehousesById,
                        localWarehousesByExternalId
                );
                variantIds.add(variant.getId());
            }
            variantCount++;
        }
        return new ImportedProduct(channelProduct.getId(), variantCount, variantIds);
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
        ProductVariant mappedVariant = channelProductVariantRepository
                .findByChannelProductIdAndExternalVariantId(channelProduct.getId(), externalSkuId)
                .map(ChannelProductVariant::getVariant)
                .orElse(null);
        ProductVariant variant = mappedVariant == null
                ? productVariantRepository.findByProductIdAndSkuAndDeletedAtIsNull(product.getId(), sellerSku)
                        .orElseGet(ProductVariant::new)
                : mappedVariant;
        boolean newVariant = variant.getId() == null;
        if (newVariant) {
            variant.setProduct(product);
            variant.setSku(uniqueSku(sellerSku, externalSkuId));
            variant.setOptionValues(Map.of("source", "TikTok Shop"));
        }
        variant.setName(product.getName());

        Object priceObject = detailSku.get("price");
        if ((!shouldPreserveLocalPrice(channelProduct, externalSkuId) || variant.getPrice() == null)
                && priceObject instanceof Map<?, ?> price) {
            variant.setPrice(moneyAmount(price.get("tax_exclusive_price")));
        }

        if (newVariant) {
            variant.setCostPrice(BigDecimal.ZERO);
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
        Object priceObject = detailSku.get("price");

        if (priceObject instanceof Map<?, ?> price) {
            mapping.setExternalPrice(
                    moneyAmount(price.get("tax_exclusive_price"))
            );
        }
        if (mapping.getSyncStatus() != SyncStatus.OUT_OF_SYNC) {
            mapping.setSyncStatus(SyncStatus.SYNCED);
            mapping.setLastSyncedAt(OffsetDateTime.now());
        }
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

    private boolean shouldPreserveLocalPrice(ChannelProduct channelProduct, String externalVariantId) {
        return channelProductVariantRepository
                .findByChannelProductIdAndExternalVariantId(channelProduct.getId(), externalVariantId)
                .map(ChannelProductVariant::getSyncStatus)
                .filter(SyncStatus.OUT_OF_SYNC::equals)
                .isPresent();
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
                                 Warehouse masterWarehouse,
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

        if (warehouseInventory.isEmpty() && masterWarehouse != null) {
            saveInventoryItem(masterWarehouse, variant, 0, 0);
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

        int newTotalQuantity = Math.max(0, availableQuantity) + Math.max(0, committedQuantity);
        int previousQuantity = item.getQuantityOnHand() == null ? 0 : item.getQuantityOnHand();
        int incomingQuantity = Math.max(0, newTotalQuantity - previousQuantity);

        item.setWarehouse(warehouse);
        item.setVariant(variant);
        item.setQuantityOnHand(newTotalQuantity);
        item.setReservedQuantity(Math.max(0, committedQuantity));
        item.setLowStockThreshold(item.getLowStockThreshold() == null ? 5 : item.getLowStockThreshold());

        if (incomingQuantity > 0) {
            BigDecimal newAvgCost = ProductCostPolicy.weightedAverageCost(
                    BigDecimal.valueOf(previousQuantity),
                    item.getAverageCost(),
                    BigDecimal.valueOf(incomingQuantity),
                    variant.getCostPrice()
            );
            item.setAverageCost(newAvgCost);
        } else if (item.getAverageCost() == null) {
            item.setAverageCost(ProductCostPolicy.initialCost(null, variant.getPrice()));
        }

        inventoryItemRepository.save(item);
    }

    private Warehouse resolveTikTokWarehouse(String externalWarehouseId, Map<String, Object> warehouseNode) {
        String marker = "[" + WAREHOUSE_MARKER + externalWarehouseId + "]";
        Warehouse warehouse = findTikTokWarehouse(externalWarehouseId).orElseGet(Warehouse::new);
        warehouse.setName("TikTok Shop - "
                + firstNonBlank(stringValue(warehouseNode.get("name")), "Warehouse")
                + " " + marker);
        String importedAddress = TikTokWarehouseAddressFormatter.format(
                map(warehouseNode.get("address")),
                externalWarehouseId
        );
        if (importedAddress != null && !importedAddress.isBlank()) {
            warehouse.setAddress(importedAddress);
        }
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
        log.setErrorSummary(error == null ? log.getErrorSummary() : error);
        log.setCompletedAt(OffsetDateTime.now());
        syncLogRepository.save(log);
    }

    private void appendSyncWarning(SyncLog syncLog, String warning) {
        String current = syncLog.getErrorSummary();
        syncLog.setErrorSummary(current == null || current.isBlank()
                ? warning
                : current + System.lineSeparator() + warning);
    }

    private String uniqueSku(String baseSku, String externalSkuId) {
        String normalizedBase = truncateSku(firstNonBlank(baseSku, fallbackSku(externalSkuId)));
        if (productVariantRepository.findBySkuAndDeletedAtIsNull(normalizedBase).isEmpty()) {
            return normalizedBase;
        }

        String suffixToken = skuSuffixToken(externalSkuId);
        String candidate = appendSkuSuffix(normalizedBase, suffixToken, 1);
        int suffix = 2;
        while (productVariantRepository.findBySkuAndDeletedAtIsNull(candidate).isPresent()) {
            candidate = appendSkuSuffix(normalizedBase, suffixToken, suffix++);
        }
        return candidate;
    }

    private String appendSkuSuffix(String baseSku, String suffixToken, int suffix) {
        String suffixValue = "-" + suffixToken + (suffix > 1 ? "-" + suffix : "");
        int baseLength = Math.max(1, MAX_VARIANT_SKU_LENGTH - suffixValue.length());
        return truncateSku(baseSku, baseLength) + suffixValue;
    }

    private String skuSuffixToken(String value) {
        String token = firstNonBlank(value, UUID.randomUUID().toString()).trim().replaceAll("[^A-Za-z0-9_-]+", "-");
        if (token.length() > 40) {
            token = token.substring(token.length() - 40);
        }
        return token.isBlank() ? UUID.randomUUID().toString() : token;
    }

    private String truncateSku(String sku) {
        return truncateSku(sku, MAX_VARIANT_SKU_LENGTH);
    }

    private String truncateSku(String sku, int maxLength) {
        if (sku == null || sku.length() <= maxLength) {
            return sku;
        }
        return sku.substring(0, maxLength);
    }

    private String fallbackSku(String externalId) {
        return "EXT-" + firstNonBlank(externalId, UUID.randomUUID().toString());
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

    private record ImportedProduct(
            UUID channelProductId,
            int variantCount,
            Set<UUID> variantIds
    ) {
    }
}
