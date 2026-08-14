package fu.osms.sync.lazada.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import fu.osms.catalog.util.ProductCostPolicy;
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
import fu.osms.sync.service.PlatformCatalogOwnershipPolicy;
import fu.osms.sync.service.SyncAlertService;
import fu.osms.sync.service.impl.ChannelProductAggregationService;
import fu.osms.sync.service.impl.SyncJobProgressTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class LazadaImportSyncServiceImpl implements LazadaImportSyncService {

    private static final int PRODUCT_PAGE_SIZE = 50;
    private static final int PRODUCT_PAGE_MAX_RETRIES = 3;
    private static final long PRODUCT_PAGE_RETRY_DELAY_MS = 1_500L;
    private static final int MAX_VARIANT_SKU_LENGTH = 100;
    private static final String WAREHOUSE_CODE_MARKER = "LAZADA_WAREHOUSE_CODE=";

    private final LazadaAuthorizedApiClient lazadaApiClient;
    private final ChannelTokenService channelTokenService;
    private final ObjectMapper objectMapper;
    private final ChannelRepository channelRepository;
    private final ChannelCredentialRepository credentialRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductImageRepository productImageRepository;
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
    private final PlatformCatalogOwnershipPolicy catalogOwnershipPolicy;
    private final ChannelProductAggregationService channelProductAggregationService;
    private final SyncJobProgressTracker syncJobProgressTracker;

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
            // Fetch category tree from Lazada for name resolution during sync
            Map<String, String> lazadaCategoryNames = fetchCategoryNames(credential);
            Map<String, Warehouse> warehouseByCode = new HashMap<>();
            for (JsonNode warehouseNode : warehouses) {
                if (isDefaultLazadaWarehouse(warehouseNode, primaryWarehouseCode)) {
                    continue;
                }

                String code = firstText(warehouseNode, "code", "id", "warehouse_id", "warehouse_code", "warehouseCode");
                if (code != null && !code.isBlank()) {
                    warehouseByCode.put(code, masterWarehouse);
                }
                warehouseCount++;
            }

            Set<UUID> changedVariantIds = new HashSet<>();
            Set<String> importedProductExternalIds = new HashSet<>();
            Set<String> importedVariantExternalIds = new HashSet<>();
            int[] importedCounts = {0, 0};
            SyncLog importSyncLog = syncLog;
            OffsetDateTime changedSince = channel.getLastSyncedAt();
            fetchProductPages(credential, changedSince, products -> {
                int pageVariantCount = 0;
                for (JsonNode productNode : products) {
                    String externalProductKey = resolveExternalProductId(productNode);
                    if (externalProductKey != null && !importedProductExternalIds.add(externalProductKey)) {
                        continue;
                    }

                    ImportedProduct imported = upsertProduct(
                            channel,
                            productNode,
                            warehouseByCode,
                            masterWarehouse,
                            primaryWarehouseCode,
                            lazadaCategoryNames,
                            importSyncLog
                    );
                    if (externalProductKey == null) {
                        importedProductExternalIds.add(imported.externalProductId());
                    }
                    importedCounts[0]++;
                    int uniqueVariantCount = 0;
                    for (String externalVariantId : imported.externalVariantIds()) {
                        if (importedVariantExternalIds.add(externalVariantId)) {
                            uniqueVariantCount++;
                        }
                    }
                    importedCounts[1] += uniqueVariantCount;
                    pageVariantCount += uniqueVariantCount;
                    changedVariantIds.addAll(imported.variantIds());
                }
                syncJobProgressTracker.recordVariantBatch(pageVariantCount);
            });
            productCount = importedCounts[0];
            variantCount = importedCounts[1];
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
            syncLog.setTotalItems(productCount + variantCount + warehouseCount);
            syncLog.setSuccessCount(productCount + variantCount + warehouseCount);
            syncLog.setFailCount(0);
            syncLog.setCompletedAt(OffsetDateTime.now());
            syncLogRepository.save(syncLog);
            marketplaceInventoryPropagationService.schedulePushAvailableStock(changedVariantIds, channelId);

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
            syncLog.setTotalItems(productCount + variantCount + warehouseCount);
            syncLog.setSuccessCount(productCount + variantCount + warehouseCount);
            syncLog.setFailCount(1);
            syncLog.setErrorSummary(e.getMessage());
            syncLog.setCompletedAt(OffsetDateTime.now());
            syncLog = syncLogRepository.save(syncLog);
            syncAlertService.notifySyncFailure(syncLog);
            throw e;
        }
    }

    private void fetchProductPages(ChannelCredential credential,
                                   OffsetDateTime changedSince,
                                   Consumer<List<JsonNode>> pageConsumer) {
        fetchProductPages(credential, changedSince, null, pageConsumer);
        fetchProductPages(credential, changedSince, "inactive", pageConsumer);
    }

    private void fetchProductPages(ChannelCredential credential,
                                   OffsetDateTime changedSince,
                                   String filter,
                                   Consumer<List<JsonNode>> pageConsumer) {
        int offset = 0;

        while (true) {
            Map<String, String> params = new HashMap<>();
            if (filter != null && !filter.isBlank()) {
                params.put("filter", filter);
            }
            params.put("limit", String.valueOf(PRODUCT_PAGE_SIZE));
            params.put("offset", String.valueOf(offset));
            params.put("options", "1");
            if (changedSince != null) {
                String lazadaIsoDate = changedSince.truncatedTo(ChronoUnit.SECONDS)
                        .withOffsetSameInstant(ZoneOffset.ofHours(-7))
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ"));

                params.put("create_after", lazadaIsoDate);
                params.put("update_after", lazadaIsoDate);
            }

            log.info("[LazadaImportSync] Calling /products/get channelId={}, filter={}, offset={}, limit={}, created_time={}, updated_time={}",
                    credential.getChannel().getId(),
                    filter == null ? "<none>" : filter,
                    offset,
                    PRODUCT_PAGE_SIZE,
                    params.get("create_after"),
                    params.get("update_after"));
            JsonNode root = fetchProductPageWithRetry(credential, params, filter, offset);

            List<JsonNode> pageProducts = toList(firstExisting(root,
                    "/data/products",
                    "/data/product",
                    "/products"
            ));
            if (!pageProducts.isEmpty()) {
                pageConsumer.accept(pageProducts);
            }

            int totalProducts = integerValue(firstExisting(root,
                    "/data/total_products",
                    "/data/total",
                    "/total_products",
                    "/total"
            ), -1);
            log.info("[LazadaImportSync] /products/get page channelId={}, filter={}, offset={}, received={}, total={}",
                    credential.getChannel().getId(),
                    filter == null ? "<none>" : filter,
                    offset,
                    pageProducts.size(),
                    totalProducts);
            if (pageProducts.isEmpty()) {
                break;
            }
            offset += pageProducts.size();
            if (totalProducts >= 0 ? offset >= totalProducts : pageProducts.size() < PRODUCT_PAGE_SIZE) {
                break;
            }

            sleepBeforeRetry(500L);
        }
    }

    private JsonNode fetchProductPageWithRetry(ChannelCredential credential,
                                               Map<String, String> params,
                                               String filter,
                                               int offset) {
        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= PRODUCT_PAGE_MAX_RETRIES; attempt++) {
            try {
                String response = lazadaApiClient.executeGet(credential.getChannel().getId(),
                        "/products/get",
                        params
                );
                JsonNode root = readTree(response);
                ensureLazadaSuccess(root, "/products/get");
                if (attempt > 1) {
                    log.info("[LazadaImportSync] /products/get retry thành công channelId={}, filter={}, offset={}, attempt={}",
                            credential.getChannel().getId(),
                            filter == null ? "<none>" : filter,
                            offset,
                            attempt);
                }
                return root;
            } catch (RuntimeException e) {
                lastError = e;
                if (attempt >= PRODUCT_PAGE_MAX_RETRIES || !isRetriableLazadaProductPageError(e)) {
                    break;
                }
                long delayMs = PRODUCT_PAGE_RETRY_DELAY_MS * attempt;
                log.warn("[LazadaImportSync] /products/get lỗi tạm thời, sẽ thử lại channelId={}, filter={}, offset={}, attempt={}/{}, delayMs={}, error={}",
                        credential.getChannel().getId(),
                        filter == null ? "<none>" : filter,
                        offset,
                        attempt,
                        PRODUCT_PAGE_MAX_RETRIES,
                        delayMs,
                        e.getMessage());
                sleepBeforeRetry(delayMs);
            }
        }
        throw lastError == null
                ? new IllegalStateException("Lazada API /products/get lỗi không xác định.")
                : lastError;
    }

    private boolean isRetriableLazadaProductPageError(RuntimeException error) {
        String message = error.getMessage() == null ? "" : error.getMessage().toLowerCase();
        return message.contains("e006")
                || message.contains("unexpected internal error")
                || message.contains("internal error")
                || message.contains("timeout")
                || message.contains("temporarily")
                || message.contains("failed to execute request to lazada")
                || message.contains("frequency exceeds the limit")
                || message.contains("system.limit");
    }

    private void sleepBeforeRetry(long delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Đã bị ngắt khi chờ thử lại Lazada API.", interrupted);
        }
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

    private boolean isDefaultLazadaWarehouse(JsonNode warehouseNode, String primaryWarehouseCode) {
        String code = firstText(warehouseNode, "code", "id", "warehouse_id", "warehouse_code", "warehouseCode");
        if (primaryWarehouseCode != null && !primaryWarehouseCode.isBlank() && primaryWarehouseCode.equals(code)) {
            return true;
        }

        String defaultFlag = firstText(warehouseNode,
                "is_default",
                "isDefault",
                "default",
                "is_primary",
                "isPrimary",
                "primary"
        );
        return defaultFlag != null
                && ("true".equalsIgnoreCase(defaultFlag)
                || "1".equals(defaultFlag)
                || "yes".equalsIgnoreCase(defaultFlag)
                || "y".equalsIgnoreCase(defaultFlag));
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
        String externalProductId = resolveExternalProductId(productNode);
        if (externalProductId == null || externalProductId.isBlank()) {
            externalProductId = UUID.randomUUID().toString();
        }
        final String resolvedExternalProductId = externalProductId;

        ChannelProduct channelProduct = channelProductRepository
                .findByChannelIdAndExternalProductId(channel.getId(), externalProductId)
                .orElseGet(ChannelProduct::new);

        Product product = channelProduct.getProduct();
        boolean initialCreate = product == null;
        if (initialCreate) {
            product = Product.builder()
                    .sku(fallbackSku(resolvedExternalProductId))
                    .attributes(new HashMap<>())
                    .build();
        }

        boolean lazadaOwned = initialCreate
                || catalogOwnershipPolicy.isPlatformOwned(channelProduct, product, externalProductId);
        if (initialCreate) {
            product.setSku(fallbackSku(externalProductId));
        }
        if (lazadaOwned) {
            product.setName(resolveProductName(productNode, externalProductId));
            product.setDescription(firstNonBlank(
                    firstText(productNode, "description", "short_description"),
                    productNode.path("attributes").path("description").asText(null)
            ));
            product.setBrand(resolveBrand(productNode));
            // Resolve and save category from Lazada category tree
            try {
                Category resolvedCategory = resolveCategory(productNode, lazadaCategoryNames);
                if (resolvedCategory != null) {
                    product.setCategory(resolvedCategory);
                }
            } catch (Exception e) {
                log.warn("[LazadaImportSync] Could not resolve category for product {}: {}",
                        externalProductId, e.getMessage());
            }
        }
        if (initialCreate) {
            product.setStatus(resolveProductStatus(firstText(productNode, "status", "seller_status")));
            product.setUnit("pcs");
        }
        product = productRepository.save(product);
        if (initialCreate) {
            insertInitialProductImages(product, extractProductImageUrls(productNode));
        }

        channelProduct.setChannel(channel);
        channelProduct.setProduct(product);
        channelProduct.setExternalProductId(externalProductId);
        channelProduct.setExternalStatus(firstText(productNode, "status", "seller_status"));
        channelProduct.setMappingState("ACTIVE");
        channelProduct.setSyncStatus(SyncStatus.SYNCED);
        channelProduct.setLastSyncedAt(OffsetDateTime.now());
        channelProduct.setLastSyncError(null);
        if (initialCreate) {
            catalogOwnershipPolicy.markPlatformImported(channelProduct);
        }
        channelProduct = channelProductRepository.save(channelProduct);
        channelProduct = channelProductAggregationService.normalizeImportedMapping(channelProduct);

        int variantCount = 0;
        Set<UUID> variantIds = new HashSet<>();
        Set<String> externalVariantIds = new HashSet<>();
        List<String> productImageUrls = extractProductImageUrls(productNode);
        for (JsonNode skuNode : extractSkus(productNode)) {
            String externalVariantId = resolveExternalVariantId(skuNode, externalProductId, variantCount);
            ProductVariant variant = upsertVariant(
                    channelProduct, product, skuNode, externalProductId, variantCount);
            if (variant == null) {
                String warning = "Remote Lazada variant is not linked to an OSMS variant: "
                        + resolveSellerSku(skuNode);
                log.warn(
                        "[LazadaImportSync] Skip unmapped remote variant for existing product channelId={} "
                                + "externalProductId={} externalVariantId={} sellerSku={}",
                        channel.getId(), externalProductId, externalVariantId, resolveSellerSku(skuNode)
                );
                appendSyncWarning(syncLog, warning);
                continue;
            }
            List<String> skuImageUrls = extractSkuImageUrls(skuNode);
            if (initialCreate) {
                insertInitialVariantImages(product, variant, skuImageUrls.isEmpty() ? productImageUrls : skuImageUrls);
            }
            upsertChannelVariant(channelProduct, variant, skuNode, variantCount);
            if (initialCreate) {
                upsertInventoryItems(variant, skuNode, warehouseByCode, masterWarehouse, primaryWarehouseCode, syncLog);
                variantIds.add(variant.getId());
            }
            externalVariantIds.add(externalProductId + "::" + externalVariantId);
            variantCount++;
        }

        if (variantCount == 0 && initialCreate) {
            String externalVariantId = resolveExternalVariantId(productNode, externalProductId, 0);
            ProductVariant variant = upsertFallbackVariant(channelProduct, product, externalProductId);
            List<String> skuImageUrls = extractSkuImageUrls(productNode);
            insertInitialVariantImages(product, variant, skuImageUrls.isEmpty() ? productImageUrls : skuImageUrls);
            upsertChannelVariant(channelProduct, variant, productNode, 0);
            upsertInventoryItems(variant, productNode, warehouseByCode, masterWarehouse, primaryWarehouseCode, syncLog);
            variantIds.add(variant.getId());
            externalVariantIds.add(externalProductId + "::" + externalVariantId);
            variantCount = 1;
        }

        return new ImportedProduct(externalProductId, variantIds, externalVariantIds);
    }

    private ProductVariant upsertVariant(ChannelProduct channelProduct,
                                         Product product,
                                         JsonNode skuNode,
                                         String externalProductId,
                                         int index) {
        String externalVariantId = resolveExternalVariantId(skuNode, externalProductId, index);
        String sellerSku = resolveSellerSku(skuNode);
        ProductVariant mappedVariant = channelProductVariantRepository
                .findByChannelProductIdAndExternalVariantId(channelProduct.getId(), externalVariantId)
                .map(ChannelProductVariant::getVariant)
                .filter(existing -> shouldReuseMappedVariant(channelProduct, existing, externalVariantId))
                .orElse(null);
        String localSku = resolveLocalVariantSku(channelProduct, sellerSku, externalProductId, externalVariantId);
        ProductVariant variant = mappedVariant == null
                ? productVariantRepository.findByProductIdAndSkuAndDeletedAtIsNull(product.getId(), localSku)
                        .orElseGet(ProductVariant::new)
                : mappedVariant;
        boolean newVariant = variant.getId() == null;
        variant.setProduct(product);
        variant.setSku(localSku);
        String variantName = product.getName();
        variant.setName(product.getName());
        variant.setBarcode(firstText(skuNode, "barcode", "BarCode", "bar_code"));
        if (!shouldPreserveLocalPrice(channelProduct, externalVariantId) || variant.getPrice() == null) {
            variant.setPrice(firstDecimal(skuNode, "price", "special_price", "sale_price", "salePrice"));
        }
        if (newVariant) {
            variant.setCostPrice(BigDecimal.ZERO);
        }
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

    private boolean isSkuUsableForExternalVariant(ChannelProduct channelProduct, String sku, String externalVariantId) {
        return productVariantRepository.findBySkuAndDeletedAtIsNull(sku)
                .map(existing -> {
                    if (existing.getId() == null) {
                        return true;
                    }

                    List<ChannelProductVariant> mappings = channelProductVariantRepository
                            .findActiveByVariantIdWithChannel(existing.getId());
                    if (mappings.isEmpty()) {
                        return false;
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

    private boolean shouldReuseMappedVariant(ChannelProduct channelProduct,
                                             ProductVariant existingVariant,
                                             String externalVariantId) {
        if (existingVariant == null || existingVariant.getId() == null) {
            return false;
        }
        return channelProductVariantRepository.findActiveByVariantIdWithChannel(existingVariant.getId())
                .stream()
                .anyMatch(mapping -> mapping.getChannelProduct() != null
                        && Objects.equals(mapping.getChannelProduct().getId(), channelProduct.getId())
                        && Objects.equals(mapping.getExternalVariantId(), externalVariantId));
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
        if (channelVariant.getSyncStatus() != SyncStatus.OUT_OF_SYNC) {
            channelVariant.setSyncStatus(SyncStatus.SYNCED);
            channelVariant.setLastSyncedAt(OffsetDateTime.now());
        }
        applyExternalVariantId(channelProduct, channelVariant, externalVariantId);
        channelProductVariantRepository.save(channelVariant);
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

    private void insertInitialProductImages(Product product, List<String> imageUrls) {
        if (product.getId() == null || imageUrls.isEmpty()) {
            return;
        }

        boolean markFirstImagePrimary = !productImageRepository.existsByProductIdAndIsPrimaryTrue(product.getId());
        productImageRepository.saveAll(buildProductImages(
                product, null, validHttpUrls(imageUrls), markFirstImagePrimary));
    }

    private void insertInitialVariantImages(Product product, ProductVariant variant, List<String> imageUrls) {
        if (variant.getId() == null || imageUrls.isEmpty()) {
            return;
        }

        productImageRepository.saveAll(buildProductImages(
                product, variant, validHttpUrls(imageUrls), false));
    }

    private List<String> validHttpUrls(List<String> imageUrls) {
        return imageUrls.stream()
                .filter(url -> url != null && (url.startsWith("http://") || url.startsWith("https://")))
                .toList();
    }

    private List<ProductImage> buildProductImages(Product product,
                                                  ProductVariant variant,
                                                  List<String> imageUrls,
                                                  boolean markFirstImagePrimary) {
        List<ProductImage> images = new ArrayList<>();
        for (int i = 0; i < imageUrls.size(); i++) {
            images.add(ProductImage.builder()
                    .product(product)
                    .variant(variant)
                    .url(imageUrls.get(i))
                    .sortOrder((short) i)
                    .isPrimary(markFirstImagePrimary && i == 0)
                    .build());
        }
        return images;
    }

    private List<String> extractProductImageUrls(JsonNode productNode) {
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        collectImageUrls(productNode.get("images"), urls);
        collectImageUrls(productNode.get("Images"), urls);
        collectImageUrls(productNode.get("marketImages"), urls);
        collectImageUrls(productNode.get("MarketImages"), urls);
        return new ArrayList<>(urls);
    }

    private List<String> extractSkuImageUrls(JsonNode skuNode) {
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        collectImageUrls(skuNode.get("Images"), urls);
        collectImageUrls(skuNode.get("images"), urls);
        collectImageUrls(skuNode.get("SkuImages"), urls);
        collectImageUrls(skuNode.get("sku_images"), urls);
        return new ArrayList<>(urls);
    }

    private void collectImageUrls(JsonNode node, Set<String> urls) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isTextual()) {
            collectImageUrlText(node.asText(), urls);
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> collectImageUrls(child, urls));
            return;
        }
        if (node.isObject()) {
            String directUrl = firstText(node, "url", "Url", "image", "Image", "src", "Src");
            if (directUrl != null) {
                collectImageUrlText(directUrl, urls);
            }
        }
    }

    private void collectImageUrlText(String value, Set<String> urls) {
        if (value == null || value.isBlank()) {
            return;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            try {
                collectImageUrls(objectMapper.readTree(trimmed), urls);
                return;
            } catch (Exception ignored) {
                // Fall through and treat the value as a plain URL.
            }
        }
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            urls.add(trimmed);
        }
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
        String importedAddress = withWarehouseCodeMarker(
                firstText(warehouseNode, "detailAddress", "address", "warehouse_address"),
                externalWarehouseId
        );
        if (importedAddress != null && !importedAddress.isBlank()) {
            warehouse.setAddress(importedAddress);
        }
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
                quantity = 0;
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
        item.setAverageCost(ProductCostPolicy.initialCost(item.getAverageCost(), variant.getCostPrice()));
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

    private String resolveExternalProductId(JsonNode productNode) {
        return firstText(productNode, "item_id", "product_id", "id");
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

    private int integerValue(JsonNode node, int fallback) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return fallback;
        }
        if (node.isInt() || node.isLong() || node.isNumber()) {
            return node.asInt(fallback);
        }
        String value = node.asText(null);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return new BigDecimal(value).intValue();
        } catch (NumberFormatException e) {
            return fallback;
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

    private void appendSyncWarning(SyncLog syncLog, String warning) {
        String current = syncLog.getErrorSummary();
        syncLog.setErrorSummary(current == null || current.isBlank()
                ? warning
                : current + System.lineSeparator() + warning);
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

    private record ImportedProduct(String externalProductId,
                                   Set<UUID> variantIds,
                                   Set<String> externalVariantIds) {
    }
}
