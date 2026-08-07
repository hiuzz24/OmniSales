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
import fu.osms.sync.entity.WebhookEvent;
import fu.osms.sync.inventory.InventoryObservation;
import fu.osms.sync.inventory.InventoryReconciliationService;
import fu.osms.sync.service.PlatformCatalogWebhookProcessor;
import fu.osms.sync.shopify.inventory.ShopifyInventoryGateway;
import fu.osms.sync.webhook.WebhookPayloadUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShopifyCatalogWebhookProcessor implements PlatformCatalogWebhookProcessor {

    private final ChannelProductRepository channelProductRepository;
    private final ChannelProductVariantRepository channelProductVariantRepository;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryReconciliationService inventoryReconciliationService;
    private final ShopifyInventoryGateway shopifyInventoryGateway;

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
        Map<Integer, String> optionNames = optionNames(payload);
        Object variantValue = payload.get("variants");
        if (variantValue instanceof List<?> variants) {
            for (Object variantObject : variants) {
                if (variantObject instanceof Map<?, ?> variantMap) {
                    upsertVariant(channelProduct, product, WebhookPayloadUtils.copyMap(variantMap), optionNames);
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

        String managedLocationId = shopifyInventoryGateway
                .resolveManagedLocationId(event.getChannel().getId());
        if (!numericId(managedLocationId).equals(numericId(locationId))) {
            return "IGNORED";
        }
        inventoryReconciliationService.observe(new InventoryObservation(
                mapping.get().getId(),
                PlatformType.SHOPIFY,
                Math.max(available, 0),
                observedAt(payload, event),
                managedLocationId,
                event.getId()
        ));
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

    private void upsertVariant(ChannelProduct channelProduct,
                               Product product,
                               Map<String, Object> variantPayload,
                               Map<Integer, String> optionNames) {
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

        updateVariant(variant, externalVariantId, variantPayload, optionNames);
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

    private void updateVariant(ProductVariant variant,
                               String externalVariantId,
                               Map<String, Object> variantPayload,
                               Map<Integer, String> optionNames) {
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
        Map<String, Object> incomingOptionValues = optionValues(variantPayload, optionNames);
        if (!incomingOptionValues.isEmpty()) {
            variant.setOptionValues(incomingOptionValues);
        }
        variant.setIsActive(true);
        variant.setDeletedAt(null);
    }

    private Map<String, Object> optionValues(Map<String, Object> variantPayload,
                                             Map<Integer, String> optionNames) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int position = 1; position <= 3; position++) {
            String optionName = optionNames.getOrDefault(position, "Option " + position);
            putIfPresent(values, normalizeOptionName(optionName), variantPayload.get("option" + position));
        }
        return values;
    }

    private Map<Integer, String> optionNames(Map<String, Object> productPayload) {
        Object value = productPayload.get("options");
        if (!(value instanceof List<?> options)) {
            return Map.of();
        }
        Map<Integer, String> names = new HashMap<>();
        for (Object optionValue : options) {
            if (!(optionValue instanceof Map<?, ?> optionMap)) {
                continue;
            }
            Map<String, Object> option = WebhookPayloadUtils.copyMap(optionMap);
            int position = WebhookPayloadUtils.integer(option.get("position"), 0);
            String name = WebhookPayloadUtils.text(option.get("name"));
            if (position > 0 && name != null && !name.isBlank()) {
                names.put(position, name.trim());
            }
        }
        return names;
    }

    private String normalizeOptionName(String name) {
        if (name == null) return "";
        String normalized = name.trim();
        if (normalized.equalsIgnoreCase("size") || normalized.equalsIgnoreCase("kích thước")) {
            return "Size";
        }
        if (normalized.equalsIgnoreCase("color")
                || normalized.equalsIgnoreCase("colour")
                || normalized.equalsIgnoreCase("màu")
                || normalized.equalsIgnoreCase("màu sắc")) {
            return "Màu";
        }
        return normalized;
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

    private OffsetDateTime observedAt(Map<String, Object> payload, WebhookEvent event) {
        Object value = WebhookPayloadUtils.firstPresent(payload, "updated_at", "updatedAt");
        if (value != null) {
            try {
                return OffsetDateTime.parse(value.toString());
            } catch (RuntimeException ignored) {
                // Fall back to the durable receive time.
            }
        }
        return event.getReceivedAt() == null ? OffsetDateTime.now() : event.getReceivedAt();
    }
}
