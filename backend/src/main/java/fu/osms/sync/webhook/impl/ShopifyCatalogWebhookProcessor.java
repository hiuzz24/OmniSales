package fu.osms.sync.webhook.impl;

import fu.osms.catalog.entity.Product;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.catalog.enums.ProductStatus;
import fu.osms.catalog.repository.ProductVariantRepository;
import fu.osms.catalog.util.ProductCostPolicy;
import fu.osms.channel.entity.ChannelProduct;
import fu.osms.channel.entity.ChannelProductVariant;
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
import fu.osms.sync.service.MarketplaceWarehouseConsistencyService;
import fu.osms.sync.service.PlatformCatalogWebhookProcessor;
import fu.osms.sync.webhook.WebhookPayloadUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShopifyCatalogWebhookProcessor implements PlatformCatalogWebhookProcessor {

    private static final String LOCATION_ID_MARKER = "SHOPIFY_LOCATION_ID=";

    private final ChannelProductRepository channelProductRepository;
    private final ChannelProductVariantRepository channelProductVariantRepository;
    private final ProductVariantRepository productVariantRepository;
    private final WarehouseRepository warehouseRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final MarketplaceWarehouseConsistencyService marketplaceWarehouseConsistencyService;

    @Override
    public PlatformType getPlatform() {
        return PlatformType.SHOPIFY;
    }

    @Override
    public boolean supports(WebhookEvent event) {
        String eventType = normalizedEventType(event);
        return eventType.startsWith("PRODUCTS_")
                || eventType.startsWith("INVENTORY_LEVELS_")
                || hasInventoryLevelPayload(eventPayload(event));
    }

    @Override
    @Transactional
    public String process(WebhookEvent event) {
        String eventType = normalizedEventType(event);
        if (eventType.startsWith("INVENTORY_LEVELS_") || hasInventoryLevelPayload(eventPayload(event))) {
            return processInventoryLevel(event);
        }
        if (eventType.startsWith("PRODUCTS_")) {
            return processProduct(event);
        }
        return "IGNORED";
    }

    @SuppressWarnings("unchecked")
    private String processProduct(WebhookEvent event) {
        Map<String, Object> payload = eventPayload(event);
        String externalProductId = numericId(WebhookPayloadUtils.text(
                WebhookPayloadUtils.firstPresent(payload, "id", "product_id", "productId", "admin_graphql_api_id")));
        if (externalProductId == null || externalProductId.isBlank()) {
            return "IGNORED";
        }

        ChannelProduct channelProduct = channelProductRepository
                .findByChannelIdAndExternalProductId(event.getChannel().getId(), externalProductId)
                .orElse(null);
        if (channelProduct == null || channelProduct.getProduct() == null) {
            log.info("[ShopifyWebhook] Ignore product webhook without local mapping channelId={} externalProductId={}",
                    event.getChannel().getId(), externalProductId);
            return "IGNORED";
        }

        Product product = channelProduct.getProduct();
        updateProduct(product, payload);
        Object variantValue = payload.get("variants");
        if (variantValue instanceof List<?> variants) {
            for (Object variantObject : variants) {
                if (variantObject instanceof Map<?, ?> variantMap) {
                    upsertVariant(channelProduct, product, WebhookPayloadUtils.copyMap(variantMap));
                }
            }
        }

        channelProduct.setExternalStatus(WebhookPayloadUtils.text(WebhookPayloadUtils.firstPresent(payload, "status")));
        channelProduct.setSyncStatus(SyncStatus.SYNCED);
        channelProduct.setLastSyncedAt(OffsetDateTime.now());
        channelProduct.setLastSyncError(null);
        channelProductRepository.save(channelProduct);
        return "PROCESSED";
    }

    private String processInventoryLevel(WebhookEvent event) {
        Map<String, Object> payload = eventPayload(event);
        String inventoryItemId = numericId(WebhookPayloadUtils.text(
                WebhookPayloadUtils.firstPresent(payload, "inventory_item_id", "inventoryItemId", "inventory_item_gid")));
        String locationId = numericId(WebhookPayloadUtils.text(
                WebhookPayloadUtils.firstPresent(payload, "location_id", "locationId", "location_gid")));
        Integer available = WebhookPayloadUtils.integer(
                WebhookPayloadUtils.firstPresent(payload, "available", "quantity", "available_quantity"), 0);

        if (inventoryItemId == null || locationId == null) {
            return "IGNORED";
        }

        Optional<ChannelProductVariant> mapping = channelProductVariantRepository
                .findActiveByChannelIdAndInventoryItemId(event.getChannel().getId(), inventoryItemId);
        if (mapping.isEmpty()) {
            log.info("[ShopifyWebhook] Ignore inventory webhook without local SKU mapping channelId={} inventoryItemId={}",
                    event.getChannel().getId(), inventoryItemId);
            return "IGNORED";
        }

        Warehouse warehouse = marketplaceWarehouseConsistencyService.resolveAndValidatePrimaryWarehouse(event.getChannel());
        upsertInventoryItem(event, warehouse, mapping.get().getVariant(), available);

        ChannelProductVariant channelVariant = mapping.get();
        channelVariant.setSyncStatus(SyncStatus.SYNCED);
        channelVariant.setLastSyncedAt(OffsetDateTime.now());
        channelProductVariantRepository.save(channelVariant);
        return "PROCESSED";
    }

    private void updateProduct(Product product, Map<String, Object> payload) {
        String title = WebhookPayloadUtils.text(WebhookPayloadUtils.firstPresent(payload, "title", "name"));
        if (title != null && !title.isBlank()) {
            product.setName(title);
        }
        String description = WebhookPayloadUtils.text(WebhookPayloadUtils.firstPresent(payload, "body_html", "description"));
        if (description != null) {
            product.setDescription(description);
        }
        String vendor = WebhookPayloadUtils.text(WebhookPayloadUtils.firstPresent(payload, "vendor", "brand"));
        if (vendor != null) {
            product.setBrand(vendor);
        }
        String status = WebhookPayloadUtils.text(WebhookPayloadUtils.firstPresent(payload, "status"));
        if (status != null && !status.isBlank()) {
            product.setStatus(toProductStatus(status));
        }
        Map<String, Object> attributes = product.getAttributes() == null
                ? new HashMap<>()
                : new HashMap<>(product.getAttributes());
        attributes.put("shopifyWebhookUpdatedAt", OffsetDateTime.now().toString());
        product.setAttributes(attributes);
    }

    private void upsertVariant(ChannelProduct channelProduct, Product product, Map<String, Object> variantPayload) {
        String externalVariantId = numericId(WebhookPayloadUtils.text(
                WebhookPayloadUtils.firstPresent(variantPayload, "id", "variant_id", "admin_graphql_api_id")));
        if (externalVariantId == null || externalVariantId.isBlank()) {
            return;
        }

        ChannelProductVariant mapping = channelProductVariantRepository
                .findByChannelProductIdAndExternalVariantId(channelProduct.getId(), externalVariantId)
                .orElse(null);
        ProductVariant variant = mapping != null
                ? mapping.getVariant()
                : resolveOrCreateVariant(product, externalVariantId, variantPayload);

        updateVariant(variant, externalVariantId, variantPayload);
        variant = productVariantRepository.save(variant);
        ProductVariant savedVariant = variant;

        if (mapping == null) {
            mapping = channelProductVariantRepository
                    .findByChannelProductIdAndVariantId(channelProduct.getId(), savedVariant.getId())
                    .orElseGet(() -> ChannelProductVariant.builder()
                            .channelProduct(channelProduct)
                            .variant(savedVariant)
                            .externalVariantId(externalVariantId)
                            .build());
        }
        mapping.setChannelProduct(channelProduct);
        mapping.setVariant(savedVariant);
        applyExternalVariantId(channelProduct, mapping, externalVariantId);
        String externalSku = usableSku(WebhookPayloadUtils.text(
                WebhookPayloadUtils.firstPresent(variantPayload, "sku")));
        mapping.setExternalSku(firstNonBlank(externalSku, mapping.getExternalSku(), variant.getSku()));
        mapping.setExternalPrice(variant.getPrice());
        mapping.setSyncStatus(SyncStatus.SYNCED);
        mapping.setLastSyncedAt(OffsetDateTime.now());

        String inventoryItemId = numericId(WebhookPayloadUtils.text(
                WebhookPayloadUtils.firstPresent(variantPayload, "inventory_item_id", "inventoryItemId")));
        if (inventoryItemId != null) {
            Map<String, Object> metadata = mapping.getMetadata() == null
                    ? new HashMap<>()
                    : new HashMap<>(mapping.getMetadata());
            metadata.put("inventory_item_id", inventoryItemId);
            mapping.setMetadata(metadata);
        }
        channelProductVariantRepository.save(mapping);
    }

    private void applyExternalVariantId(ChannelProduct channelProduct,
                                        ChannelProductVariant mapping,
                                        String externalVariantId) {
        channelProductVariantRepository
                .findByChannelProductIdAndExternalVariantId(channelProduct.getId(), externalVariantId)
                .filter(existing -> mapping.getId() != null && !Objects.equals(existing.getId(), mapping.getId()))
                .ifPresentOrElse(
                        existing -> {
                            Map<String, Object> metadata = mapping.getMetadata() == null
                                    ? new HashMap<>()
                                    : new HashMap<>(mapping.getMetadata());
                            metadata.put("conflictingShopifyWebhookExternalVariantId", externalVariantId);
                            metadata.put("conflictingChannelProductVariantId", existing.getId().toString());
                            mapping.setMetadata(metadata);
                        },
                        () -> mapping.setExternalVariantId(externalVariantId)
                );
    }

    private ProductVariant resolveOrCreateVariant(Product product, String externalVariantId, Map<String, Object> variantPayload) {
        String sku = usableSku(WebhookPayloadUtils.text(WebhookPayloadUtils.firstPresent(variantPayload, "sku")));
        if (sku != null) {
            Optional<ProductVariant> existing = productVariantRepository.findByProductIdAndSkuAndDeletedAtIsNull(product.getId(), sku);
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        return ProductVariant.builder()
                .product(product)
                .sku(firstNonBlank(sku, fallbackSku(externalVariantId)))
                .price(BigDecimal.ZERO)
                .costPrice(BigDecimal.ZERO)
                .isActive(true)
                .optionValues(new HashMap<>())
                .build();
    }

    private void updateVariant(ProductVariant variant, String externalVariantId, Map<String, Object> variantPayload) {
        String sku = usableSku(WebhookPayloadUtils.text(WebhookPayloadUtils.firstPresent(variantPayload, "sku")));
        if (sku != null && canUseSku(variant, sku)) {
            variant.setSku(sku);
        } else if (variant.getSku() == null || variant.getSku().isBlank()) {
            variant.setSku(fallbackSku(externalVariantId));
        }

        String title = WebhookPayloadUtils.text(WebhookPayloadUtils.firstPresent(variantPayload, "title", "name"));
        if (title != null && !title.isBlank() && !"Default Title".equalsIgnoreCase(title)) {
            variant.setName(title);
        }
        String barcode = WebhookPayloadUtils.text(WebhookPayloadUtils.firstPresent(variantPayload, "barcode"));
        if (barcode != null) {
            variant.setBarcode(barcode.isBlank() ? null : barcode);
        }
        Object price = WebhookPayloadUtils.firstPresent(variantPayload, "price");
        if (price != null) {
            variant.setPrice(WebhookPayloadUtils.decimal(price));
        }
        variant.setCostPrice(ProductCostPolicy.initialCost(variant.getCostPrice(), variant.getPrice()));
        Integer weight = WebhookPayloadUtils.integer(WebhookPayloadUtils.firstPresent(variantPayload, "grams", "weight"), 0);
        if (weight != null && weight > 0) {
            variant.setWeightGrams(weight);
        }
        variant.setOptionValues(optionValues(variantPayload));
        variant.setIsActive(true);
        variant.setDeletedAt(null);
    }

    private void upsertInventoryItem(WebhookEvent event, Warehouse warehouse, ProductVariant variant, int available) {
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
        int after = Math.max(available, 0);
        int reserved = Math.min(Math.max(safeInt(item.getReservedQuantity()), 0), after);
        item.setQuantityOnHand(after);
        item.setReservedQuantity(reserved);
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
                    .note("Shopify inventory webhook")
                    .performedAt(OffsetDateTime.now())
                    .build());
        }
    }

    private Warehouse resolveShopifyWarehouse(String locationId) {
        for (Warehouse warehouse : warehouseRepository.findByDeletedAtIsNull()) {
            if (locationId.equals(extractMarkerValue(warehouse.getAddress(), LOCATION_ID_MARKER))) {
                return warehouse;
            }
        }

        String name = "Shopify - Location " + locationId;
        Warehouse warehouse = warehouseRepository.findFirstByNameAndDeletedAtIsNull(name)
                .orElseGet(() -> Warehouse.builder()
                        .name(name)
                        .isActive(true)
                        .build());
        warehouse.setAddress("[" + LOCATION_ID_MARKER + locationId + "]");
        warehouse.setIsActive(true);
        return warehouseRepository.save(warehouse);
    }

    private Map<String, Object> optionValues(Map<String, Object> variantPayload) {
        Map<String, Object> values = new HashMap<>();
        putIfPresent(values, "Option 1", variantPayload.get("option1"));
        putIfPresent(values, "Option 2", variantPayload.get("option2"));
        putIfPresent(values, "Option 3", variantPayload.get("option3"));
        return values;
    }

    private void putIfPresent(Map<String, Object> map, String key, Object value) {
        if (value != null && !value.toString().isBlank()) {
            map.put(key, value.toString());
        }
    }

    private boolean hasInventoryLevelPayload(Map<String, Object> payload) {
        return payload != null
                && WebhookPayloadUtils.firstPresent(payload, "inventory_item_id", "inventoryItemId", "inventory_item_gid") != null
                && WebhookPayloadUtils.firstPresent(payload, "location_id", "locationId", "location_gid") != null;
    }

    private Map<String, Object> eventPayload(WebhookEvent event) {
        Map<String, Object> payload = event.getRawPayload();
        if (payload == null) {
            return Map.of();
        }
        for (String key : List.of("data", "product", "inventory_level", "payload")) {
            Object value = payload.get(key);
            if (value instanceof Map<?, ?> map) {
                return WebhookPayloadUtils.copyMap(map);
            }
        }
        return payload;
    }

    private ProductStatus toProductStatus(String status) {
        String normalized = status.toLowerCase();
        if ("active".equals(normalized)) {
            return ProductStatus.ACTIVE;
        }
        if ("draft".equals(normalized)) {
            return ProductStatus.DRAFT;
        }
        return ProductStatus.INACTIVE;
    }

    private boolean canUseSku(ProductVariant currentVariant, String sku) {
        return productVariantRepository.findBySkuAndDeletedAtIsNull(sku)
                .map(existing -> existing.getId().equals(currentVariant.getId()))
                .orElse(true);
    }

    private String usableSku(String sku) {
        return sku == null || sku.isBlank() ? null : sku.trim();
    }

    private String fallbackSku(String externalId) {
        return "EXT-" + firstNonBlank(externalId, java.util.UUID.randomUUID().toString());
    }

    private String normalizedEventType(WebhookEvent event) {
        return event.getEventType() == null ? "" : event.getEventType().toUpperCase();
    }

    private String numericId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        int queryIndex = value.indexOf('?');
        if (queryIndex >= 0) {
            value = value.substring(0, queryIndex);
        }
        int slashIndex = value.lastIndexOf('/');
        return slashIndex >= 0 ? value.substring(slashIndex + 1) : value;
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
