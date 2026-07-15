package fu.osms.sync.shopify.impl;

import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelCredential;
import fu.osms.channel.entity.ChannelProductVariant;
import fu.osms.channel.repository.ChannelCredentialRepository;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.inventory.entity.InventoryItem;
import fu.osms.inventory.entity.Warehouse;
import fu.osms.inventory.repository.InventoryIssueRepository;
import fu.osms.inventory.repository.InventoryItemRepository;
import fu.osms.inventory.repository.StockReceiveRepository;
import fu.osms.sync.shopify.ShopifyApiClient;
import fu.osms.sync.shopify.ShopifyInventoryUpdateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShopifyInventoryUpdateServiceImpl implements ShopifyInventoryUpdateService {

    private static final int BATCH_SIZE = 100;
    private static final String LOCATION_ID_MARKER = "SHOPIFY_LOCATION_ID=";

    private final ShopifyApiClient shopifyApiClient;
    private final ChannelRepository channelRepository;
    private final ChannelCredentialRepository credentialRepository;
    private final ChannelProductVariantRepository channelProductVariantRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final StockReceiveRepository stockReceiveRepository;
    private final InventoryIssueRepository inventoryIssueRepository;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public int syncChangedAvailableStock(UUID channelId,
                                         OffsetDateTime changedSince,
                                         OffsetDateTime changedUntil,
                                         Collection<UUID> productChangedVariantIds) {
        Channel channel = channelRepository.findById(channelId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.CHANNEL_NOT_FOUND));

        ChannelCredential credential = credentialRepository.findByChannelIdAndConnectionState(channelId, "CONNECTED")
                .orElseThrow(() -> new IllegalStateException("Kênh Shopify chưa có token kết nối."));
        if (credential.getAccessToken() == null || credential.getAccessToken().isBlank()) {
            throw new IllegalStateException("Kênh Shopify chưa có access token.");
        }

        ChangedScope changedScope = resolveChangedScope(changedSince, changedUntil, productChangedVariantIds);
        if (changedSince != null && changedScope.variantIds().isEmpty()) {
            log.info("[ShopifyStockSync] No stock changes found since {}. Skip Shopify API call.", changedSince);
            return 0;
        }

        List<ChannelProductVariant> mappings = changedSince == null
                ? channelProductVariantRepository.findActiveByChannelIdWithVariant(channelId)
                : channelProductVariantRepository.findActiveByChannelIdAndVariantIdInWithVariant(
                        channelId,
                        new ArrayList<>(changedScope.variantIds())
                );
        if (mappings.isEmpty()) {
            return 0;
        }

        String shopDomain = extractShopDomain(channel);
        String defaultLocationId = resolveLocationId(channel, shopDomain, credential.getAccessToken());
        UUID defaultWarehouseId = resolveDefaultWarehouseId(channel);

        List<UUID> variantIds = mappings.stream()
                .map(mapping -> mapping.getVariant().getId())
                .distinct()
                .toList();
        Map<UUID, List<InventoryItem>> inventoryByVariantId = inventoryItemRepository.findByVariantIdIn(variantIds)
                .stream()
                .filter(item -> matchesDefaultWarehouse(item, defaultWarehouseId))
                .collect(Collectors.groupingBy(item -> item.getVariant().getId()));

        int pushedCount = 0;
        List<Map<String, Object>> quantities = new ArrayList<>();
        List<ChannelProductVariant> batchMappings = new ArrayList<>();

        for (ChannelProductVariant mapping : mappings) {
            String inventoryItemId = extractInventoryItemId(mapping);
            if (inventoryItemId == null) {
                log.warn(
                        "[ShopifyStockSync] Skip variant without inventory_item_id channelId={} variantId={}",
                        channelId,
                        mapping.getVariant().getId()
                );
                continue;
            }

            Map<String, Integer> availableByLocationId = totalAvailableByShopifyLocationId(
                    inventoryByVariantId.getOrDefault(mapping.getVariant().getId(), List.of()),
                    defaultLocationId
            );
            for (Map.Entry<String, Integer> entry : availableByLocationId.entrySet()) {
                Map<String, Object> quantityPayload = new HashMap<>();
                quantityPayload.put("inventoryItemId", toInventoryItemGid(inventoryItemId));
                quantityPayload.put("locationId", entry.getKey());
                quantityPayload.put("quantity", entry.getValue());
                quantityPayload.put("changeFromQuantity", null);
                quantities.add(quantityPayload);
                batchMappings.add(mapping);

                if (quantities.size() == BATCH_SIZE) {
                    pushedCount += sendBatch(shopDomain, credential.getAccessToken(), channelId, quantities, batchMappings);
                    quantities.clear();
                    batchMappings.clear();
                }
            }
        }

        if (!quantities.isEmpty()) {
            pushedCount += sendBatch(shopDomain, credential.getAccessToken(), channelId, quantities, batchMappings);
        }

        return pushedCount;
    }

    private ChangedScope resolveChangedScope(OffsetDateTime changedSince,
                                             OffsetDateTime changedUntil,
                                             Collection<UUID> productChangedVariantIds) {
        if (changedSince == null) {
            return new ChangedScope(Set.of());
        }

        Set<UUID> variantIds = new HashSet<>();
        if (productChangedVariantIds != null) {
            variantIds.addAll(productChangedVariantIds);
        }

        OffsetDateTime effectiveChangedUntil = changedUntil == null ? OffsetDateTime.now() : changedUntil;
        variantIds.addAll(stockReceiveRepository.findChangedConfirmedVariantIdsBetween(changedSince, effectiveChangedUntil));
        variantIds.addAll(inventoryIssueRepository.findChangedAppliedVariantIdsBetween(changedSince, effectiveChangedUntil));

        log.info(
                "[ShopifyStockSync] Resolved stock documents changedSince={} changedUntil={} variantCount={}",
                changedSince,
                effectiveChangedUntil,
                variantIds.size()
        );

        return new ChangedScope(variantIds);
    }

    @SuppressWarnings("unchecked")
    private int sendBatch(String shopDomain,
                          String accessToken,
                          UUID channelId,
                          List<Map<String, Object>> quantities,
                          List<ChannelProductVariant> mappings) {
        ensureInventoryItemsStockedAtLocations(shopDomain, accessToken, quantities);

        String query = """
                mutation inventorySetQuantities($input: InventorySetQuantitiesInput!, $idempotencyKey: String!) {
                  inventorySetQuantities(input: $input) @idempotent(key: $idempotencyKey) {
                    inventoryAdjustmentGroup {
                      reason
                      referenceDocumentUri
                      changes {
                        name
                        delta
                        quantityAfterChange
                      }
                    }
                    userErrors {
                      code
                      field
                      message
                    }
                  }
                }
                """;

        Map<String, Object> input = new HashMap<>();
        input.put("name", "available");
        input.put("reason", "correction");
        input.put("referenceDocumentUri", "omnisales://channels/" + channelId + "/inventory-sync/" + UUID.randomUUID());
        input.put("quantities", List.copyOf(quantities));

        log.info("[ShopifyStockSync] Set Shopify inventory channelId={} quantityCount={} quantities={}",
                channelId, quantities.size(), quantities);

        Map<String, Object> response = shopifyApiClient.executeGraphQl(
                shopDomain,
                accessToken,
                query,
                Map.of(
                        "input", input,
                        "idempotencyKey", UUID.randomUUID().toString()
                )
        );
        ensureNoGraphQlErrors(response);

        Map<String, Object> data = (Map<String, Object>) response.get("data");
        Map<String, Object> payload = data == null ? null : (Map<String, Object>) data.get("inventorySetQuantities");
        List<Map<String, Object>> userErrors = payload == null ? List.of() : (List<Map<String, Object>>) payload.get("userErrors");
        if (userErrors != null && !userErrors.isEmpty()) {
            throw new IllegalStateException("Shopify inventorySetQuantities lỗi: " + userErrors);
        }

        OffsetDateTime syncedAt = OffsetDateTime.now();
        for (ChannelProductVariant mapping : mappings) {
            mapping.setLastSyncedAt(syncedAt);
            channelProductVariantRepository.save(mapping);
        }

        return mappings.size();
    }

    private void ensureInventoryItemsStockedAtLocations(String shopDomain,
                                                        String accessToken,
                                                        List<Map<String, Object>> quantities) {
        Map<String, Set<String>> requestedLocationIdsByInventoryItemId = new HashMap<>();
        Map<String, Integer> quantityByPair = new HashMap<>();
        for (Map<String, Object> quantity : quantities) {
            String inventoryItemId = stringValue(quantity.get("inventoryItemId"));
            String locationId = stringValue(quantity.get("locationId"));
            if (inventoryItemId == null || locationId == null) {
                continue;
            }

            String pairKey = inventoryItemId + "|" + locationId;
            requestedLocationIdsByInventoryItemId
                    .computeIfAbsent(inventoryItemId, ignored -> new HashSet<>())
                    .add(locationId);
            quantityByPair.put(pairKey, intValue(quantity.get("quantity")));
        }

        if (requestedLocationIdsByInventoryItemId.isEmpty()) {
            return;
        }

        Map<String, Set<String>> stockedLocationIdsByInventoryItemId = fetchStockedLocationIds(
                shopDomain,
                accessToken,
                requestedLocationIdsByInventoryItemId.keySet()
        );

        for (Map.Entry<String, Set<String>> requestedEntry : requestedLocationIdsByInventoryItemId.entrySet()) {
            String inventoryItemId = requestedEntry.getKey();
            Set<String> stockedLocationIds = stockedLocationIdsByInventoryItemId.getOrDefault(inventoryItemId, Set.of());
            for (String locationId : requestedEntry.getValue()) {
                if (stockedLocationIds.contains(locationId)) {
                    continue;
                }

                int availableQuantity = quantityByPair.getOrDefault(inventoryItemId + "|" + locationId, 0);
                activateInventoryItemAtLocation(shopDomain, accessToken, inventoryItemId, locationId, availableQuantity);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Set<String>> fetchStockedLocationIds(String shopDomain,
                                                            String accessToken,
                                                            Collection<String> inventoryItemIds) {
        String query = """
                query inventoryItemsStockedLocations($ids: [ID!]!) {
                  nodes(ids: $ids) {
                    ... on InventoryItem {
                      id
                      inventoryLevels(first: 100) {
                        nodes {
                          location {
                            id
                          }
                        }
                      }
                    }
                  }
                }
                """;

        Map<String, Set<String>> locationIdsByInventoryItemId = new HashMap<>();
        List<String> ids = new ArrayList<>(inventoryItemIds);
        for (int start = 0; start < ids.size(); start += BATCH_SIZE) {
            List<String> batchIds = ids.subList(start, Math.min(start + BATCH_SIZE, ids.size()));
            Map<String, Object> response = shopifyApiClient.executeGraphQl(
                    shopDomain,
                    accessToken,
                    query,
                    Map.of("ids", batchIds)
            );
            ensureNoGraphQlErrors(response);

            Map<String, Object> data = (Map<String, Object>) response.get("data");
            List<Map<String, Object>> nodes = data == null ? List.of() : (List<Map<String, Object>>) data.get("nodes");
            if (nodes == null) {
                continue;
            }

            for (Map<String, Object> node : nodes) {
                if (node == null) {
                    continue;
                }
                String inventoryItemId = stringValue(node.get("id"));
                if (inventoryItemId == null) {
                    continue;
                }
                Set<String> locationIds = locationIdsByInventoryItemId
                        .computeIfAbsent(inventoryItemId, ignored -> new HashSet<>());
                Map<String, Object> inventoryLevels = (Map<String, Object>) node.get("inventoryLevels");
                List<Map<String, Object>> levelNodes = inventoryLevels == null
                        ? List.of()
                        : (List<Map<String, Object>>) inventoryLevels.get("nodes");
                if (levelNodes == null) {
                    continue;
                }
                for (Map<String, Object> levelNode : levelNodes) {
                    Map<String, Object> location = levelNode == null
                            ? null
                            : (Map<String, Object>) levelNode.get("location");
                    String locationId = location == null ? null : stringValue(location.get("id"));
                    if (locationId != null) {
                        locationIds.add(locationId);
                    }
                }
            }
        }

        return locationIdsByInventoryItemId;
    }

    @SuppressWarnings("unchecked")
    private void activateInventoryItemAtLocation(String shopDomain,
                                                 String accessToken,
                                                 String inventoryItemId,
                                                 String locationId,
                                                 int availableQuantity) {
        String mutation = """
                mutation inventoryActivate($inventoryItemId: ID!, $locationId: ID!, $available: Int, $idempotencyKey: String!) {
                  inventoryActivate(inventoryItemId: $inventoryItemId, locationId: $locationId, available: $available) @idempotent(key: $idempotencyKey) {
                    inventoryLevel {
                      id
                    }
                    userErrors {
                      field
                      message
                    }
                  }
                }
                """;

        log.info(
                "[ShopifyStockSync] Activate Shopify inventory item inventoryItemId={} locationId={} available={}",
                inventoryItemId,
                locationId,
                availableQuantity
        );

        Map<String, Object> response = shopifyApiClient.executeGraphQl(
                shopDomain,
                accessToken,
                mutation,
                Map.of(
                        "inventoryItemId", inventoryItemId,
                        "locationId", locationId,
                        "available", availableQuantity,
                        "idempotencyKey", UUID.randomUUID().toString()
                )
        );
        ensureNoGraphQlErrors(response);

        Map<String, Object> data = (Map<String, Object>) response.get("data");
        Map<String, Object> payload = data == null ? null : (Map<String, Object>) data.get("inventoryActivate");
        List<Map<String, Object>> userErrors = payload == null ? List.of() : (List<Map<String, Object>>) payload.get("userErrors");
        if (userErrors != null && !userErrors.isEmpty() && !isAlreadyActiveUserError(userErrors)) {
            throw new IllegalStateException("Shopify inventoryActivate lỗi: " + userErrors);
        }
    }

    private boolean isAlreadyActiveUserError(List<Map<String, Object>> userErrors) {
        for (Map<String, Object> userError : userErrors) {
            String message = stringValue(userError.get("message"));
            if (message == null) {
                continue;
            }
            String normalized = message.toLowerCase();
            if (normalized.contains("already") && (normalized.contains("active") || normalized.contains("stock"))) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private String resolveLocationId(Channel channel, String shopDomain, String accessToken) {
        Map<String, Object> metadata = channel.getMetadata() == null
                ? new HashMap<>()
                : new HashMap<>(channel.getMetadata());
        Object configuredLocationId = metadata.get("shopifyLocationId");
        if (configuredLocationId != null && !configuredLocationId.toString().isBlank()) {
            return toLocationGid(configuredLocationId.toString());
        }

        Map<String, Object> response = shopifyApiClient.executeGraphQl(
                shopDomain,
                accessToken,
                "query { locations(first: 1) { nodes { id } } }",
                Map.of()
        );
        ensureNoGraphQlErrors(response);

        Map<String, Object> data = (Map<String, Object>) response.get("data");
        Map<String, Object> locations = data == null ? null : (Map<String, Object>) data.get("locations");
        List<Map<String, Object>> nodes = locations == null ? List.of() : (List<Map<String, Object>>) locations.get("nodes");
        if (nodes == null || nodes.isEmpty() || nodes.get(0).get("id") == null) {
            throw new IllegalStateException("Không tìm thấy Shopify location để cập nhật tồn kho.");
        }

        String locationId = nodes.get(0).get("id").toString();
        metadata.put("shopifyLocationId", locationId);
        channel.setMetadata(metadata);
        channelRepository.save(channel);
        return locationId;
    }

    private void ensureNoGraphQlErrors(Map<String, Object> response) {
        Object errors = response.get("errors");
        if (errors != null) {
            throw new IllegalStateException("Shopify GraphQL errors: " + errors);
        }
    }

    private String extractShopDomain(Channel channel) {
        if (channel.getMetadata() != null && channel.getMetadata().get("shopDomain") != null) {
            return channel.getMetadata().get("shopDomain").toString();
        }
        return channel.getDisplayName();
    }

    private String extractInventoryItemId(ChannelProductVariant mapping) {
        if (mapping.getMetadata() == null) {
            return null;
        }
        Object id = mapping.getMetadata().get("inventory_item_id");
        return id == null ? null : id.toString();
    }

    private UUID resolveDefaultWarehouseId(Channel channel) {
        if (channel.getMetadata() == null) {
            return null;
        }

        Object warehouseId = channel.getMetadata().get("defaultWarehouseId");
        if (warehouseId == null || warehouseId.toString().isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(warehouseId.toString());
        } catch (IllegalArgumentException ignored) {
            log.warn("[ShopifyStockSync] Ignore invalid defaultWarehouseId={} channelId={}", warehouseId, channel.getId());
            return null;
        }
    }

    private boolean matchesDefaultWarehouse(InventoryItem item, UUID defaultWarehouseId) {
        if (defaultWarehouseId == null) {
            return true;
        }
        return item.getWarehouse() != null && defaultWarehouseId.equals(item.getWarehouse().getId());
    }

    private String toInventoryItemGid(String value) {
        if (value.startsWith("gid://shopify/InventoryItem/")) {
            return value;
        }
        return "gid://shopify/InventoryItem/" + value;
    }

    private String toLocationGid(String value) {
        if (value.startsWith("gid://shopify/Location/")) {
            return value;
        }
        return "gid://shopify/Location/" + value;
    }

    private Map<String, Integer> totalAvailableByShopifyLocationId(List<InventoryItem> inventoryItems, String defaultLocationId) {
        Map<String, Integer> availableByLocationId = new HashMap<>();
        if (inventoryItems == null || inventoryItems.isEmpty()) {
            availableByLocationId.put(defaultLocationId, 0);
            return availableByLocationId;
        }

        for (InventoryItem inventoryItem : inventoryItems) {
            String locationId = extractShopifyLocationId(inventoryItem.getWarehouse());
            if (locationId != null) {
                availableByLocationId.merge(locationId, availableQuantity(inventoryItem), Integer::sum);
            }
        }

        if (!availableByLocationId.isEmpty()) {
            return availableByLocationId;
        }

        int totalAvailableQuantity = 0;
        for (InventoryItem inventoryItem : inventoryItems) {
            totalAvailableQuantity += availableQuantity(inventoryItem);
        }
        availableByLocationId.put(defaultLocationId, totalAvailableQuantity);
        return availableByLocationId;
    }

    private int availableQuantity(InventoryItem inventoryItem) {
        if (inventoryItem.getAvailableQuantity() != null) {
            return inventoryItem.getAvailableQuantity();
        }
        int quantityOnHand = inventoryItem.getQuantityOnHand() != null ? inventoryItem.getQuantityOnHand() : 0;
        int reservedQuantity = inventoryItem.getReservedQuantity() != null ? inventoryItem.getReservedQuantity() : 0;
        return quantityOnHand - reservedQuantity;
    }

    private String extractShopifyLocationId(Warehouse warehouse) {
        if (warehouse == null || warehouse.getAddress() == null) {
            return null;
        }
        String address = warehouse.getAddress();
        int markerIndex = address.indexOf("[" + LOCATION_ID_MARKER);
        if (markerIndex >= 0) {
            int start = markerIndex + LOCATION_ID_MARKER.length() + 1;
            int end = address.indexOf(']', start);
            if (end > start) {
                return toLocationGid(address.substring(start, end));
            }
        }

        String legacyMarker = "Shopify location id:";
        int legacyIndex = address.indexOf(legacyMarker);
        if (legacyIndex >= 0) {
            String value = address.substring(legacyIndex + legacyMarker.length()).trim();
            if (!value.isBlank()) {
                return toLocationGid(value);
            }
        }
        return null;
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

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null || value.toString().isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private record ChangedScope(Set<UUID> variantIds) {
    }
}
