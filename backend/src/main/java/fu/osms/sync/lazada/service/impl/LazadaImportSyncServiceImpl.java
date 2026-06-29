package fu.osms.sync.lazada.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.catalog.entity.Product;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.catalog.enums.ProductStatus;
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
import fu.osms.inventory.repository.InventoryItemRepository;
import fu.osms.inventory.repository.WarehouseRepository;
import fu.osms.sync.entity.SyncLog;
import fu.osms.sync.lazada.service.LazadaApiClient;
import fu.osms.sync.lazada.service.LazadaImportSyncService;
import fu.osms.sync.repository.SyncLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LazadaImportSyncServiceImpl implements LazadaImportSyncService {

    private static final int PRODUCT_PAGE_SIZE = 50;
    private static final String WAREHOUSE_CODE_MARKER = "LAZADA_WAREHOUSE_CODE=";

    private final LazadaApiClient lazadaApiClient;
    private final ObjectMapper objectMapper;
    private final ChannelRepository channelRepository;
    private final ChannelCredentialRepository credentialRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ChannelProductRepository channelProductRepository;
    private final ChannelProductVariantRepository channelProductVariantRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final WarehouseRepository warehouseRepository;
    private final SyncLogRepository syncLogRepository;

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
            List<JsonNode> warehouses = fetchWarehouses(credential);
            Map<String, Warehouse> warehouseByCode = new HashMap<>();
            for (JsonNode warehouseNode : warehouses) {
                Warehouse warehouse = upsertWarehouse(warehouseNode);
                warehouseCount++;

                String code = firstText(warehouseNode, "code", "id", "warehouse_id", "warehouse_code");
                if (code != null && !code.isBlank()) {
                    warehouseByCode.put(code, warehouse);
                }
            }

            List<JsonNode> products = fetchProducts(credential);
            for (JsonNode productNode : products) {
                ImportedProduct imported = upsertProduct(channel, productNode, warehouseByCode);
                productCount += imported.productSaved ? 1 : 0;
                variantCount += imported.variantCount;
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
            syncLogRepository.save(syncLog);
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

            String response = lazadaApiClient.executeGet(
                    "/products/get",
                    params,
                    credential.getAccessToken(),
                    tokenExpiresAt(credential)
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
        String response = lazadaApiClient.executeGet(
                "/rc/warehouse/get",
                Map.of(),
                credential.getAccessToken(),
                tokenExpiresAt(credential)
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

    private ImportedProduct upsertProduct(Channel channel, JsonNode productNode, Map<String, Warehouse> warehouseByCode) {
        String externalProductId = firstText(productNode, "item_id", "product_id", "id");
        if (externalProductId == null || externalProductId.isBlank()) {
            externalProductId = "LAZADA-" + UUID.randomUUID();
        }

        ChannelProduct channelProduct = channelProductRepository
                .findByChannelIdAndExternalProductId(channel.getId(), externalProductId)
                .orElseGet(ChannelProduct::new);

        Product product = channelProduct.getProduct();
        if (product == null) {
            product = productRepository.findFirstBySkuAndDeletedAtIsNull("LAZADA-" + externalProductId)
                    .orElseGet(Product::new);
        }

        product.setSku(product.getSku() == null ? "LAZADA-" + externalProductId : product.getSku());
        product.setName(resolveProductName(productNode, externalProductId));
        product.setDescription(firstNonBlank(
                firstText(productNode, "description", "short_description"),
                productNode.path("attributes").path("description").asText(null)
        ));
        product.setBrand(resolveBrand(productNode));
        product.setUnit(product.getUnit() == null ? "pcs" : product.getUnit());
        product.setStatus(resolveProductStatus(firstText(productNode, "status", "seller_status")));
        product.setAttributes(toMap(productNode));
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

        int variantCount = 0;
        for (JsonNode skuNode : extractSkus(productNode)) {
            ProductVariant variant = upsertVariant(product, skuNode, externalProductId, variantCount);
            upsertChannelVariant(channelProduct, variant, skuNode, variantCount);
            upsertInventoryItems(variant, skuNode, warehouseByCode);
            variantCount++;
        }

        if (variantCount == 0) {
            ProductVariant variant = upsertFallbackVariant(product, externalProductId);
            upsertChannelVariant(channelProduct, variant, productNode, 0);
            upsertInventoryItems(variant, productNode, warehouseByCode);
            variantCount = 1;
        }

        return new ImportedProduct(true, variantCount);
    }

    private ProductVariant upsertVariant(Product product, JsonNode skuNode, String externalProductId, int index) {
        String sellerSku = firstText(skuNode, "SellerSku", "seller_sku", "ShopSku", "shop_sku", "sku");
        String localSku = sellerSku == null || sellerSku.isBlank()
                ? "LAZADA-" + externalProductId + "-" + index
                : "LAZADA-" + externalProductId + "-" + sellerSku;

        ProductVariant variant = productVariantRepository.findBySkuAndDeletedAtIsNull(localSku)
                .orElseGet(ProductVariant::new);
        variant.setProduct(product);
        variant.setSku(localSku);
        variant.setName(firstNonBlank(firstText(skuNode, "name", "SkuName", "sku_name"), product.getName()));
        variant.setBarcode(firstText(skuNode, "barcode", "BarCode"));
        variant.setPrice(firstDecimal(skuNode, "price", "special_price", "sale_price"));
        variant.setIsActive(true);
        variant.setOptionValues(toMap(skuNode));
        variant.setWeightGrams(firstInteger(skuNode, "package_weight", "weight"));
        return productVariantRepository.save(variant);
    }

    private ProductVariant upsertFallbackVariant(Product product, String externalProductId) {
        String sku = "LAZADA-" + externalProductId + "-DEFAULT";
        ProductVariant variant = productVariantRepository.findBySkuAndDeletedAtIsNull(sku)
                .orElseGet(ProductVariant::new);
        variant.setProduct(product);
        variant.setSku(sku);
        variant.setName(product.getName());
        variant.setPrice(BigDecimal.ZERO);
        variant.setIsActive(true);
        variant.setOptionValues(Map.of("default", true));
        return productVariantRepository.save(variant);
    }

    private void upsertChannelVariant(ChannelProduct channelProduct, ProductVariant variant, JsonNode skuNode, int index) {
        String externalVariantId = firstText(skuNode, "SkuId", "sku_id", "ShopSku", "SellerSku", "seller_sku");
        if (externalVariantId == null || externalVariantId.isBlank()) {
            externalVariantId = variant.getSku() + "-" + index;
        }

        ChannelProductVariant channelVariant = channelProductVariantRepository
                .findByChannelProductIdAndExternalVariantId(channelProduct.getId(), externalVariantId)
                .orElseGet(ChannelProductVariant::new);
        channelVariant.setChannelProduct(channelProduct);
        channelVariant.setVariant(variant);
        channelVariant.setExternalVariantId(externalVariantId);
        channelVariant.setExternalSku(firstNonBlank(firstText(skuNode, "SellerSku", "seller_sku", "ShopSku"), variant.getSku()));
        channelVariant.setExternalPrice(firstDecimal(skuNode, "price", "special_price", "sale_price"));
        channelVariant.setSyncStatus(SyncStatus.SYNCED);
        channelVariant.setLastSyncedAt(OffsetDateTime.now());
        channelVariant.setMetadata(toMap(skuNode));
        channelProductVariantRepository.save(channelVariant);
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

    private void upsertInventoryItems(ProductVariant variant, JsonNode skuNode, Map<String, Warehouse> warehouseByCode) {
        List<JsonNode> warehouseInventories = toList(firstExisting(skuNode,
                "/multiWarehouseInventories",
                "/channelInventories",
                "/fblWarehouseInventories"
        ));

        if (warehouseInventories.isEmpty()) {
            Integer quantity = firstInteger(skuNode, "quantity", "Available", "sellableStock");
            if (quantity == null) {
                return;
            }

            Warehouse warehouse = resolveWarehouse("dropshipping", warehouseByCode);
            upsertInventoryItem(warehouse, variant, quantity, 0);
            return;
        }

        for (JsonNode inventoryNode : warehouseInventories) {
            String warehouseCode = firstText(inventoryNode, "warehouseCode", "warehouse_code", "code");
            Warehouse warehouse = resolveWarehouse(warehouseCode, warehouseByCode);

            int quantityOnHand = firstIntegerOrDefault(inventoryNode,
                    0,
                    "totalQuantity",
                    "quantity",
                    "sellableQuantity"
            );
            int reservedQuantity = firstIntegerOrDefault(inventoryNode, 0, "occupyQuantity")
                    + firstIntegerOrDefault(inventoryNode, 0, "withholdQuantity");

            if (reservedQuantity > quantityOnHand) {
                reservedQuantity = quantityOnHand;
            }
            upsertInventoryItem(warehouse, variant, quantityOnHand, reservedQuantity);
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

    private void upsertInventoryItem(Warehouse warehouse, ProductVariant variant, int quantityOnHand, int reservedQuantity) {
        InventoryItem item = inventoryItemRepository
                .findByWarehouseIdAndVariantId(warehouse.getId(), variant.getId())
                .orElseGet(() -> InventoryItem.builder()
                        .warehouse(warehouse)
                        .variant(variant)
                        .lowStockThreshold(variant.getProduct().getLowStockThreshold())
                        .build());

        item.setQuantityOnHand(Math.max(quantityOnHand, 0));
        item.setReservedQuantity(Math.max(reservedQuantity, 0));
        if (item.getLowStockThreshold() == null) {
            item.setLowStockThreshold(variant.getProduct().getLowStockThreshold());
        }
        inventoryItemRepository.save(item);
    }

    private List<JsonNode> extractSkus(JsonNode productNode) {
        JsonNode skus = firstExisting(productNode, "/skus", "/Skus", "/sku_list", "/data/skus");
        return toList(skus);
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

    private Map<String, Object> toMap(JsonNode node) {
        return objectMapper.convertValue(node, new TypeReference<>() {});
    }

    private record ImportedProduct(boolean productSaved, int variantCount) {
    }
}
