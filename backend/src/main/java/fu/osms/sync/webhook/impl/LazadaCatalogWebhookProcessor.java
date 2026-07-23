package fu.osms.sync.webhook.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.channel.entity.ChannelCredential;
import fu.osms.catalog.entity.Product;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.catalog.enums.ProductStatus;
import fu.osms.catalog.repository.ProductVariantRepository;
import fu.osms.catalog.util.ProductCostPolicy;
import fu.osms.channel.entity.ChannelProduct;
import fu.osms.channel.entity.ChannelProductVariant;
import fu.osms.channel.repository.ChannelCredentialRepository;
import fu.osms.channel.repository.ChannelProductRepository;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.enums.SyncStatus;
import fu.osms.inventory.entity.InventoryItem;
import fu.osms.inventory.entity.InventoryTransaction;
import fu.osms.inventory.entity.Warehouse;
import fu.osms.inventory.enums.InvTxnType;
import fu.osms.inventory.repository.InventoryItemRepository;
import fu.osms.inventory.repository.InventoryTransactionRepository;
import fu.osms.inventory.repository.WarehouseRepository;
import fu.osms.sync.entity.WebhookEvent;
import fu.osms.sync.lazada.service.LazadaAuthorizedApiClient;
import fu.osms.sync.service.MarketplaceWarehouseConsistencyService;
import fu.osms.sync.service.PlatformCatalogWebhookProcessor;
import fu.osms.sync.webhook.WebhookPayloadUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class LazadaCatalogWebhookProcessor implements PlatformCatalogWebhookProcessor {

    private static final int MAX_VARIANT_SKU_LENGTH = 100;
    private static final String WAREHOUSE_CODE_MARKER = "LAZADA_WAREHOUSE_CODE=";

    private final ChannelProductRepository channelProductRepository;
    private final ChannelProductVariantRepository channelProductVariantRepository;
    private final ChannelCredentialRepository channelCredentialRepository;
    private final ProductVariantRepository productVariantRepository;
    private final WarehouseRepository warehouseRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final LazadaAuthorizedApiClient lazadaApiClient;
    private final ObjectMapper objectMapper;
    private final MarketplaceWarehouseConsistencyService marketplaceWarehouseConsistencyService;

    @Override
    public PlatformType getPlatform() {
        return PlatformType.LAZADA;
    }

    @Override
    public boolean supports(WebhookEvent event) {
        String eventType = normalizedEventType(event);
        if (eventType.contains("ORDER") || eventType.contains("TRADE") || eventType.contains("REVERSE")) {
            return false;
        }
        Map<String, Object> payload = eventPayload(event);
        return containsAny(eventType, "PRODUCT", "ITEM", "SKU", "STOCK", "INVENTORY")
                || firstNonBlank(text(payload, "item_id", "itemId", "product_id", "productId"),
                text(payload, "sku_id", "SkuId", "skuId", "seller_sku", "SellerSku")) != null;
    }

    @Override
    @Transactional
    public String process(WebhookEvent event) {
        Map<String, Object> payload = eventPayload(event);
        String externalProductId = firstNonBlank(text(payload, "item_id", "itemId", "product_id", "productId"));
        payload = enrichWithRemoteProduct(event, payload, externalProductId);
        externalProductId = firstNonBlank(text(payload, "item_id", "itemId", "product_id", "productId"), externalProductId);
        ChannelProduct channelProduct = externalProductId == null
                ? null
                : channelProductRepository.findByChannelIdAndExternalProductId(event.getChannel().getId(), externalProductId)
                .orElse(null);

        if (channelProduct != null && channelProduct.getProduct() != null) {
            updateProduct(channelProduct.getProduct(), payload);
            processSkuPayloads(event, channelProduct, payload);
            channelProduct.setExternalStatus(firstNonBlank(text(payload, "status"), text(payload, "item_status", "product_status")));
            channelProduct.setSyncStatus(SyncStatus.SYNCED);
            channelProduct.setLastSyncedAt(OffsetDateTime.now());
            channelProduct.setLastSyncError(null);
            channelProductRepository.save(channelProduct);
            return "PROCESSED";
        }

        ChannelProductVariant mapping = resolveMappedVariant(event, payload).orElse(null);
        if (mapping == null) {
            log.info("[LazadaWebhook] Ignore catalog webhook without local mapping channelId={} externalProductId={}",
                    event.getChannel().getId(), externalProductId);
            return "IGNORED";
        }

        updateVariant(mapping.getVariant(), payload);
        productVariantRepository.save(mapping.getVariant());
        updateMapping(mapping, payload);
        processInventoryIfPresent(event, mapping.getVariant(), payload);
        return "PROCESSED";
    }

    private void processSkuPayloads(WebhookEvent event, ChannelProduct channelProduct, Map<String, Object> payload) {
        boolean processedList = false;
        int index = 0;
        for (String key : List.of("skus", "Skus", "SKUs", "sku_list", "skuList", "seller_skus", "Sku")) {
            Object value = payload.get(key);
            if (value instanceof List<?> list) {
                for (Object skuObject : list) {
                    if (skuObject instanceof Map<?, ?> skuMap) {
                        upsertSku(event, channelProduct, WebhookPayloadUtils.copyMap(skuMap), index++);
                        processedList = true;
                    }
                }
            }
        }
        if (!processedList) {
            upsertSku(event, channelProduct, payload, 0);
        }
    }

    private void upsertSku(WebhookEvent event, ChannelProduct channelProduct, Map<String, Object> skuPayload, int index) {
        String externalVariantId = resolveExternalVariantId(skuPayload, channelProduct.getExternalProductId(), index);
        String sellerSku = resolveSellerSku(skuPayload);
        if (externalVariantId == null && sellerSku == null) {
            processInventoryIfPresent(event, null, skuPayload);
            return;
        }

        ChannelProductVariant mapping = externalVariantId == null
                ? null
                : channelProductVariantRepository
                .findByChannelProductIdAndExternalVariantId(channelProduct.getId(), externalVariantId)
                .orElse(null);
        if (mapping == null && sellerSku != null) {
            mapping = resolveMappedVariant(event, skuPayload)
                    .filter(candidate -> candidate.getChannelProduct().getId().equals(channelProduct.getId()))
                    .orElse(null);
        }
        ProductVariant variant = mapping != null && shouldReuseMappedVariant(channelProduct, mapping.getVariant())
                ? mapping.getVariant()
                : resolveOrCreateVariant(channelProduct, externalVariantId, sellerSku);

        updateVariant(variant, skuPayload);
        variant = productVariantRepository.save(variant);

        ChannelProductVariant variantMapping = channelProductVariantRepository
                .findByChannelProductIdAndVariantId(channelProduct.getId(), variant.getId())
                .orElse(null);
        if (mapping == null) {
            mapping = variantMapping == null
                    ? ChannelProductVariant.builder()
                    .channelProduct(channelProduct)
                    .variant(variant)
                    .externalVariantId(firstNonBlank(externalVariantId, sellerSku))
                    .build()
                    : variantMapping;
        } else if (variantMapping != null && !Objects.equals(mapping.getId(), variantMapping.getId())) {
            mapping = variantMapping;
        } else {
            mapping.setVariant(variant);
        }
        updateMapping(mapping, skuPayload);
        processInventoryIfPresent(event, variant, skuPayload);
    }

    private Optional<ChannelProductVariant> resolveMappedVariant(WebhookEvent event, Map<String, Object> payload) {
        String externalProductId = firstNonBlank(text(payload, "item_id", "itemId", "product_id", "productId"));
        String externalVariantId = firstNonBlank(
                directExternalVariantId(payload),
                externalProductId == null ? null : resolveExternalVariantId(payload, externalProductId, 0)
        );
        if (externalVariantId != null) {
            Optional<ChannelProductVariant> byId = channelProductVariantRepository
                    .findActiveByChannelIdAndExternalVariantId(event.getChannel().getId(), externalVariantId);
            if (byId.isPresent()) {
                return byId;
            }
        }

        String sellerSku = resolveSellerSku(payload);
        if (sellerSku == null) {
            return Optional.empty();
        }
        return channelProductVariantRepository.findActiveByChannelIdWithVariant(event.getChannel().getId()).stream()
                .filter(mapping -> sellerSku.equals(mapping.getExternalSku())
                        || (mapping.getVariant() != null && sellerSku.equals(mapping.getVariant().getSku())))
                .findFirst();
    }

    private String resolveExternalVariantId(Map<String, Object> payload, String externalProductId, int index) {
        String externalVariantId = directExternalVariantId(payload);
        if ((externalVariantId == null || externalVariantId.isBlank())
                && externalProductId != null && !externalProductId.isBlank()) {
            externalVariantId = externalProductId + "-SKU-" + index;
        }
        return externalVariantId;
    }

    private String directExternalVariantId(Map<String, Object> payload) {
        return firstNonBlank(text(payload,
                "SkuId",
                "sku_id",
                "skuId",
                "ShopSku",
                "shop_sku",
                "shopSku",
                "SellerSku",
                "seller_sku",
                "sellerSku",
                "sellerSKU"));
    }

    private String resolveSellerSku(Map<String, Object> payload) {
        return firstNonBlank(text(payload,
                "SellerSku",
                "seller_sku",
                "sellerSku",
                "sellerSKU",
                "seller_sku_id",
                "sku",
                "shop_sku",
                "ShopSku",
                "shopSku"));
    }

    private void updateProduct(Product product, Map<String, Object> payload) {
        Map<String, Object> lazadaAttributes = mapValue(payload, "attributes");
        String name = firstNonBlank(
                text(payload, "name", "title", "product_name", "item_name"),
                text(lazadaAttributes, "name", "Name")
        );
        if (name != null) {
            product.setName(name);
        }
        String description = firstNonBlank(
                text(payload, "description", "short_description", "product_description"),
                text(lazadaAttributes, "description", "Description")
        );
        if (description != null) {
            product.setDescription(description);
        }
        String brand = firstNonBlank(text(payload, "brand", "Brand"), text(lazadaAttributes, "brand", "Brand"));
        if (brand != null) {
            product.setBrand(brand);
        }
        String status = firstNonBlank(text(payload, "status", "item_status", "product_status"));
        if (status != null) {
            product.setStatus(toProductStatus(status));
        }
        Map<String, Object> attributes = product.getAttributes() == null
                ? new HashMap<>()
                : new HashMap<>(product.getAttributes());
        attributes.putAll(payload);
        attributes.put("lazadaWebhookUpdatedAt", OffsetDateTime.now().toString());
        product.setAttributes(attributes);
    }

    private ProductVariant resolveOrCreateVariant(ChannelProduct channelProduct, String externalVariantId, String sellerSku) {
        Product product = channelProduct.getProduct();
        String sku = resolveLocalVariantSku(channelProduct, sellerSku, channelProduct.getExternalProductId(), externalVariantId);
        Optional<ProductVariant> existing = productVariantRepository.findByProductIdAndSkuAndDeletedAtIsNull(product.getId(), sku);
        return existing.orElseGet(() -> ProductVariant.builder()
                .product(product)
                .sku(sku)
                .price(BigDecimal.ZERO)
                .costPrice(BigDecimal.ZERO)
                .isActive(true)
                .optionValues(new HashMap<>())
                .build());
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

    private boolean isSkuUsableForExternalVariant(ChannelProduct channelProduct, String sku, String externalVariantId) {
        return productVariantRepository.findBySkuAndDeletedAtIsNull(sku)
                .map(existing -> {
                    if (existing.getId() == null) {
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

    private String fallbackSku(String externalId) {
        return "EXT-" + firstNonBlank(externalId, java.util.UUID.randomUUID().toString());
    }

    private String truncateSku(String sku, int maxLength) {
        String normalized = sku == null ? "" : sku.trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength);
    }

    private void updateVariant(ProductVariant variant, Map<String, Object> payload) {
        String sellerSku = resolveSellerSku(payload);
        if (sellerSku != null && canUseSku(variant, sellerSku)) {
            variant.setSku(sellerSku);
        }
        String name = firstNonBlank(text(payload, "name", "sku_name", "SkuName", "variation", "title"));
        if (name != null) {
            variant.setName(name);
        }
        Object price = firstPresent(payload,
                "price",
                "Price",
                "sale_price",
                "salePrice",
                "SalePrice",
                "special_price",
                "specialPrice",
                "SpecialPrice");
        if (price != null) {
            variant.setPrice(WebhookPayloadUtils.decimal(price));
        }
        variant.setCostPrice(ProductCostPolicy.initialCost(variant.getCostPrice(), variant.getPrice()));
        variant.setOptionValues(optionValues(payload));
        variant.setIsActive(true);
        variant.setDeletedAt(null);
    }

    private void updateMapping(ChannelProductVariant mapping, Map<String, Object> payload) {
        String externalVariantId = firstNonBlank(directExternalVariantId(payload), mapping.getExternalVariantId());
        if (externalVariantId != null && !externalVariantId.isBlank()) {
            channelProductVariantRepository
                    .findByChannelProductIdAndExternalVariantId(mapping.getChannelProduct().getId(), externalVariantId)
                    .filter(existing -> mapping.getId() != null && !Objects.equals(existing.getId(), mapping.getId()))
                    .ifPresentOrElse(
                            existing -> {
                                Map<String, Object> metadata = mapping.getMetadata() == null
                                        ? new HashMap<>()
                                        : new HashMap<>(mapping.getMetadata());
                                metadata.put("conflictingLazadaWebhookExternalVariantId", externalVariantId);
                                metadata.put("conflictingChannelProductVariantId", existing.getId().toString());
                                mapping.setMetadata(metadata);
                            },
                            () -> mapping.setExternalVariantId(externalVariantId)
                    );
        }
        mapping.setExternalSku(firstNonBlank(resolveSellerSku(payload), mapping.getVariant().getSku()));
        mapping.setExternalPrice(mapping.getVariant().getPrice());
        mapping.setSyncStatus(SyncStatus.SYNCED);
        mapping.setLastSyncedAt(OffsetDateTime.now());
        channelProductVariantRepository.save(mapping);
    }

    private void processInventoryIfPresent(WebhookEvent event, ProductVariant fallbackVariant, Map<String, Object> payload) {
        ProductVariant variant = fallbackVariant;
        if (variant == null) {
            variant = resolveMappedVariant(event, payload)
                    .map(ChannelProductVariant::getVariant)
                    .orElse(null);
        }
        if (variant == null) {
            return;
        }
        Warehouse masterWarehouse = marketplaceWarehouseConsistencyService.resolveAndValidatePrimaryWarehouse(event.getChannel());

        boolean processedWarehouseList = false;
        for (String key : List.of("multiWarehouseInventories", "channelInventories", "fblWarehouseInventories",
                "warehouseInventories", "stock_list", "warehouses")) {
            Object value = payload.get(key);
            if (value instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> stockMap) {
                        Map<String, Object> stockPayload = WebhookPayloadUtils.copyMap(stockMap);
                        upsertInventoryItem(event, masterWarehouse, variant,
                                quantityFrom(stockPayload), reservedFrom(stockPayload));
                        processedWarehouseList = true;
                    }
                }
            }
        }

        if (!processedWarehouseList) {
            Integer quantity = quantityFrom(payload);
            if (quantity != null) {
                upsertInventoryItem(event, masterWarehouse, variant, quantity, reservedFrom(payload));
            }
        }
    }

    private void upsertInventoryItem(WebhookEvent event, Warehouse warehouse, ProductVariant variant, Integer quantity, int reservedQuantity) {
        if (quantity == null) {
            return;
        }
        InventoryItem item = inventoryItemRepository
                .findByWarehouseIdAndVariantId(warehouse.getId(), variant.getId())
                .orElseGet(() -> InventoryItem.builder()
                        .warehouse(warehouse)
                        .variant(variant)
                        .lowStockThreshold(variant.getProduct().getLowStockThreshold())
                        .averageCost(ProductCostPolicy.initialCost(
                                variant.getCostPrice(),
                                variant.getPrice()))
                        .build());

        int before = safeInt(item.getQuantityOnHand());
        int after = Math.max(quantity, 0);
        item.setQuantityOnHand(after);
        int reserved = reservedQuantity > 0 ? reservedQuantity : safeInt(item.getReservedQuantity());
        item.setReservedQuantity(Math.min(Math.max(reserved, 0), after));
        item.setAverageCost(ProductCostPolicy.initialCost(item.getAverageCost(), variant.getCostPrice()));
        inventoryItemRepository.save(item);

        int delta = after - before;
        if (delta != 0) {
            inventoryTransactionRepository.save(InventoryTransaction.builder()
                    .warehouse(warehouse)
                    .variant(variant)
                    .type(InvTxnType.ADJUSTMENT)
                    .referenceType("ADJUSTMENT")
                    .referenceId(event.getId())
                    .quantityChange(delta)
                    .quantityBefore(before)
                    .quantityAfter(after)
                    .unitCost(BigDecimal.ZERO)
                    .note("Lazada inventory webhook")
                    .performedAt(OffsetDateTime.now())
                    .build());
        }
    }

    private Warehouse resolveLazadaWarehouse(Map<String, Object> payload) {
        String warehouseCode = firstNonBlank(
                text(payload, "warehouseCode", "warehouse_code", "warehouseId", "warehouse_id", "code", "id"),
                "dropshipping"
        );
        for (Warehouse warehouse : warehouseRepository.findByDeletedAtIsNull()) {
            String marker = extractMarkerValue(warehouse.getAddress(), WAREHOUSE_CODE_MARKER);
            if (warehouseCode.equals(marker) || hasLegacyWarehouseCode(warehouse.getAddress(), warehouseCode)) {
                return warehouse;
            }
        }

        String name = "Lazada Warehouse " + warehouseCode;
        Warehouse warehouse = warehouseRepository.findFirstByNameAndDeletedAtIsNull(name)
                .orElseGet(() -> Warehouse.builder()
                        .name(name)
                        .isActive(true)
                        .build());
        warehouse.setAddress("[" + WAREHOUSE_CODE_MARKER + warehouseCode + "]");
        warehouse.setIsActive(true);
        return warehouseRepository.save(warehouse);
    }

    private Integer quantityFrom(Map<String, Object> payload) {
        Object quantity = firstPresent(payload,
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
                "quantity",
                "Quantity",
                "real_quantity",
                "realQuantity",
                "totalQuantity",
                "total_quantity",
                "quantityOnHand",
                "quantity_on_hand",
                "stock",
                "Stock");
        return quantity == null ? null : WebhookPayloadUtils.integer(quantity, 0);
    }

    private int reservedFrom(Map<String, Object> payload) {
        int occupy = WebhookPayloadUtils.integer(firstPresent(payload,
                "occupy_quantity",
                "occupyQuantity",
                "reservedQuantity",
                "reserved_quantity",
                "reserved"), 0);
        int withhold = WebhookPayloadUtils.integer(firstPresent(payload,
                "withhold_quantity",
                "withholdQuantity",
                "holdQuantity",
                "hold_quantity"), 0);
        return Math.max(occupy, 0) + Math.max(withhold, 0);
    }

    private Map<String, Object> enrichWithRemoteProduct(WebhookEvent event,
                                                        Map<String, Object> payload,
                                                        String externalProductId) {
        if (!shouldFetchRemoteProduct(event, payload, externalProductId)) {
            return payload;
        }
        return fetchRemoteProduct(event, payload, externalProductId)
                .map(remoteProduct -> {
                    Map<String, Object> enriched = new HashMap<>(remoteProduct);
                    if (externalProductId != null) {
                        enriched.putIfAbsent("item_id", externalProductId);
                    }
                    log.info("[LazadaWebhook] Loaded remote product detail channelId={} externalProductId={}",
                            event.getChannel().getId(), firstNonBlank(text(enriched, "item_id", "product_id"), externalProductId));
                    return enriched;
                })
                .orElse(payload);
    }

    private boolean shouldFetchRemoteProduct(WebhookEvent event, Map<String, Object> payload, String externalProductId) {
        String eventType = normalizedEventType(event);
        boolean productEvent = eventType.contains("PRODUCT") || eventType.contains("ITEM") || eventType.contains("SKU");
        boolean hasPrice = firstPresent(payload,
                "price",
                "Price",
                "sale_price",
                "salePrice",
                "special_price",
                "specialPrice") != null;
        return (productEvent || externalProductId != null) && !hasPrice;
    }

    private Optional<Map<String, Object>> fetchRemoteProduct(WebhookEvent event,
                                                            Map<String, Object> payload,
                                                            String externalProductId) {
        ChannelCredential credential = channelCredentialRepository.findByChannelId(event.getChannel().getId())
                .orElse(null);
        if (credential == null || credential.getAccessToken() == null || credential.getAccessToken().isBlank()) {
            log.warn("[LazadaWebhook] Cannot fetch product detail without credential channelId={}", event.getChannel().getId());
            return Optional.empty();
        }

        Set<String> sellerSkus = extractSellerSkus(payload);
        Optional<JsonNode> remote = fetchRemoteProductBySellerSkus(
                event.getChannel().getId(), externalProductId, sellerSkus);
        if (remote.isEmpty() && externalProductId != null) {
            remote = fetchRemoteProductBySearch(event.getChannel().getId(), externalProductId, sellerSkus);
        }
        if (remote.isEmpty()) {
            log.warn("[LazadaWebhook] Remote product detail not found channelId={} externalProductId={} sellerSkus={}",
                    event.getChannel().getId(), externalProductId, sellerSkus);
            return Optional.empty();
        }
        return Optional.of(objectMapper.convertValue(remote.get(), new com.fasterxml.jackson.core.type.TypeReference<>() {
        }));
    }

    private Optional<JsonNode> fetchRemoteProductBySellerSkus(UUID channelId,
                                                             String externalProductId,
                                                             Set<String> sellerSkus) {
        if (sellerSkus.isEmpty()) {
            return Optional.empty();
        }
        Optional<JsonNode> product = fetchRemoteProductBySellerSkuParam(
                channelId,
                externalProductId,
                sellerSkus,
                "sku_seller_list"
        );
        if (product.isPresent()) {
            return product;
        }
        return fetchRemoteProductBySellerSkuParam(
                channelId,
                externalProductId,
                sellerSkus,
                "SkuSellerList"
        );
    }

    private Optional<JsonNode> fetchRemoteProductBySellerSkuParam(UUID channelId,
                                                                 String externalProductId,
                                                                 Set<String> sellerSkus,
                                                                 String paramName) {
        Map<String, String> params = baseProductFetchParams();
        try {
            params.put(paramName, objectMapper.writeValueAsString(sellerSkus));
        } catch (Exception e) {
            params.put(paramName, sellerSkus.toString());
        }
        return fetchRemoteProductsSafely(channelId, params).stream()
                .filter(product -> matchesProduct(product, externalProductId, sellerSkus))
                .findFirst();
    }

    private Optional<JsonNode> fetchRemoteProductBySearch(UUID channelId,
                                                         String externalProductId,
                                                         Set<String> sellerSkus) {
        List<String> searchValues = new ArrayList<>();
        searchValues.add(externalProductId);
        searchValues.addAll(sellerSkus);
        for (String search : searchValues) {
            if (search == null || search.isBlank()) {
                continue;
            }
            Map<String, String> params = baseProductFetchParams();
            params.put("search", search);
            Optional<JsonNode> product = fetchRemoteProductsSafely(channelId, params).stream()
                    .filter(candidate -> matchesProduct(candidate, externalProductId, sellerSkus))
                    .findFirst();
            if (product.isPresent()) {
                return product;
            }
        }
        return Optional.empty();
    }

    private Map<String, String> baseProductFetchParams() {
        Map<String, String> params = new HashMap<>();
        params.put("filter", "all");
        params.put("limit", "50");
        params.put("offset", "0");
        params.put("options", "1");
        return params;
    }

    private List<JsonNode> fetchRemoteProductsSafely(UUID channelId, Map<String, String> params) {
        try {
            return fetchRemoteProducts(channelId, params);
        } catch (Exception e) {
            log.warn("[LazadaWebhook] Failed to fetch product detail params={}: {}", params.keySet(), e.getMessage());
            return List.of();
        }
    }

    private List<JsonNode> fetchRemoteProducts(UUID channelId, Map<String, String> params) {
        String response = lazadaApiClient.executeGet(channelId,
                "/products/get",
                params
        );
        JsonNode root = readTree(response);
        ensureLazadaSuccess(root, "/products/get");
        return toList(firstExisting(root, "/data/products", "/data/product", "/products"));
    }

    private JsonNode readTree(String response) {
        try {
            return objectMapper.readTree(response);
        } catch (Exception e) {
            throw new IllegalStateException("Không đọc được response từ Lazada.", e);
        }
    }

    private void ensureLazadaSuccess(JsonNode root, String apiPath) {
        String code = firstText(root, "code");
        if (code != null && !code.isBlank() && !"0".equals(code)) {
            String message = firstNonBlank(firstText(root, "message", "msg", "error_msg"), "Unknown error");
            throw new IllegalStateException("Lazada API " + apiPath + " lỗi: " + message);
        }
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

    private boolean matchesProduct(JsonNode product, String externalProductId, Set<String> sellerSkus) {
        String itemId = firstText(product, "item_id", "itemId", "product_id", "productId", "id");
        if (externalProductId != null && externalProductId.equals(itemId)) {
            return true;
        }
        if (sellerSkus.isEmpty()) {
            return false;
        }
        for (JsonNode skuNode : extractSkuNodes(product)) {
            String sellerSku = firstText(skuNode, "seller_sku", "SellerSku", "sellerSku", "sku");
            if (sellerSku != null && sellerSkus.contains(sellerSku)) {
                return true;
            }
        }
        return false;
    }

    private List<JsonNode> extractSkuNodes(JsonNode productNode) {
        List<JsonNode> skus = toList(firstExisting(productNode,
                "/skus",
                "/Skus",
                "/SKUs",
                "/sku_list",
                "/skuList",
                "/data/skus",
                "/data/Skus"));
        if (!skus.isEmpty()) {
            return skus;
        }
        for (String key : List.of("skus", "Skus", "SKUs", "sku_list", "skuList")) {
            JsonNode value = productNode.get(key);
            skus = toList(value);
            if (!skus.isEmpty()) {
                return skus;
            }
        }
        return List.of();
    }

    private Set<String> extractSellerSkus(Map<String, Object> payload) {
        Set<String> sellerSkus = new LinkedHashSet<>();
        collectSellerSku(payload, sellerSkus);
        return sellerSkus;
    }

    private void collectSellerSku(Object value, Set<String> sellerSkus) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = WebhookPayloadUtils.copyMap(map);
            String sellerSku = resolveSellerSku(copy);
            if (sellerSku != null) {
                sellerSkus.add(sellerSku);
            }
            for (Map.Entry<String, Object> entry : copy.entrySet()) {
                if (isSkuListKey(entry.getKey())) {
                    collectSkuListValue(entry.getValue(), sellerSkus);
                } else {
                    collectSellerSku(entry.getValue(), sellerSkus);
                }
            }
            return;
        }
        if (value instanceof List<?> list) {
            for (Object child : list) {
                collectSellerSku(child, sellerSkus);
            }
        }
    }

    private void collectSkuListValue(Object value, Set<String> sellerSkus) {
        if (value instanceof List<?> list) {
            for (Object child : list) {
                if (child instanceof String text && !text.isBlank()) {
                    sellerSkus.add(text.trim());
                } else {
                    collectSellerSku(child, sellerSkus);
                }
            }
            return;
        }
        collectSellerSku(value, sellerSkus);
    }

    private boolean isSkuListKey(String key) {
        return key != null && List.of(
                "sku_list",
                "skuList",
                "seller_skus",
                "sellerSkuList",
                "SellerSkuList",
                "sku_seller_list",
                "SkuSellerList"
        ).contains(key);
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

    private Map<String, Object> eventPayload(WebhookEvent event) {
        Map<String, Object> payload = event.getRawPayload();
        if (payload == null) {
            return Map.of();
        }
        for (String key : List.of("data", "payload", "product", "item")) {
            Object value = payload.get(key);
            if (value instanceof Map<?, ?> dataMap) {
                return WebhookPayloadUtils.copyMap(dataMap);
            }
        }
        return payload;
    }

    private Object firstPresent(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            Object value = payload.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String text(Map<String, Object> payload, String... keys) {
        return WebhookPayloadUtils.text(firstPresent(payload, keys));
    }

    private Map<String, Object> mapValue(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value instanceof Map<?, ?> map ? WebhookPayloadUtils.copyMap(map) : Map.of();
    }

    private Map<String, Object> optionValues(Map<String, Object> payload) {
        Object options = firstPresent(payload, "saleProp", "sale_property", "properties", "variation");
        if (options instanceof Map<?, ?> map) {
            return WebhookPayloadUtils.copyMap(map);
        }
        if (options != null && !options.toString().isBlank()) {
            Map<String, Object> values = new HashMap<>();
            values.put("option", options.toString());
            return values;
        }
        return new HashMap<>();
    }

    private ProductStatus toProductStatus(String status) {
        String normalized = status.toLowerCase();
        if (normalized.contains("active") || normalized.contains("live")) {
            return ProductStatus.ACTIVE;
        }
        if (normalized.contains("draft")) {
            return ProductStatus.DRAFT;
        }
        return ProductStatus.INACTIVE;
    }

    private boolean canUseSku(ProductVariant currentVariant, String sku) {
        return productVariantRepository.findBySkuAndDeletedAtIsNull(sku)
                .map(existing -> existing.getId().equals(currentVariant.getId()))
                .orElse(true);
    }

    private boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private String normalizedEventType(WebhookEvent event) {
        return event.getEventType() == null ? "" : event.getEventType().toUpperCase();
    }

    private String extractMarkerValue(String address, String marker) {
        if (address == null) {
            return null;
        }
        int markerIndex = address.indexOf("[" + marker);
        if (markerIndex < 0) {
            return null;
        }
        int start = markerIndex + marker.length() + 1;
        int end = address.indexOf(']', start);
        return end > start ? address.substring(start, end) : null;
    }

    private boolean hasLegacyWarehouseCode(String address, String warehouseCode) {
        return address != null
                && address.toLowerCase().contains("lazada warehouse code:")
                && address.toLowerCase().contains(warehouseCode.toLowerCase());
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }
}
