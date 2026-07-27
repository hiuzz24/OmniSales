package fu.osms.sync.shopify.inventory.impl;

import fu.osms.channel.entity.Channel;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.channel.token.service.ChannelTokenService;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.sync.inventory.PlatformInventoryException;
import fu.osms.sync.shopify.ShopifyApiClient;
import fu.osms.sync.shopify.ShopifyShopDomainNormalizer;
import fu.osms.sync.shopify.inventory.InventoryLocationKey;
import fu.osms.sync.shopify.inventory.ShopifyInventoryGateway;
import fu.osms.sync.shopify.inventory.ShopifyInventorySetCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShopifyInventoryGatewayImpl implements ShopifyInventoryGateway {

    private static final int BATCH_SIZE = 100;

    private final ShopifyApiClient apiClient;
    private final ChannelRepository channelRepository;
    private final ChannelTokenService tokenService;
    private final ShopifyShopDomainNormalizer domainNormalizer;

    @Override
    public Map<InventoryLocationKey, Integer> readAvailable(
            UUID channelId,
            Collection<InventoryLocationKey> keys
    ) {
        if (keys == null || keys.isEmpty()) {
            return Map.of();
        }
        Channel channel = requireChannel(channelId);
        String domain = shopDomain(channel);
        List<String> inventoryItemIds = keys.stream()
                .map(InventoryLocationKey::inventoryItemId)
                .map(this::toInventoryItemGid)
                .distinct()
                .toList();

        return tokenService.execute(channelId, token -> {
            Map<InventoryLocationKey, Integer> result = new HashMap<>();
            for (int start = 0; start < inventoryItemIds.size(); start += BATCH_SIZE) {
                List<String> batch = inventoryItemIds.subList(
                        start, Math.min(start + BATCH_SIZE, inventoryItemIds.size()));
                readBatch(domain, token.accessToken(), batch, result);
            }
            return result;
        });
    }

    @Override
    public int setAvailable(
            UUID channelId,
            Collection<ShopifyInventorySetCommand> commands,
            String idempotencyKey
    ) {
        if (commands == null || commands.isEmpty()) {
            return 0;
        }
        Channel channel = requireChannel(channelId);
        String domain = shopDomain(channel);
        String stableKey = idempotencyKey == null || idempotencyKey.isBlank()
                ? UUID.randomUUID().toString()
                : idempotencyKey;
        List<ShopifyInventorySetCommand> commandList = List.copyOf(commands);

        return tokenService.execute(channelId, token -> {
            int count = 0;
            for (int start = 0; start < commandList.size(); start += BATCH_SIZE) {
                List<ShopifyInventorySetCommand> batch = commandList.subList(
                        start, Math.min(start + BATCH_SIZE, commandList.size()));
                ensureStocked(domain, token.accessToken(), batch);
                writeBatch(channelId, domain, token.accessToken(), batch,
                        stableKey + "-" + (start / BATCH_SIZE));
                count += batch.size();
            }
            return count;
        });
    }

    @Override
    public String resolveManagedLocationId(UUID channelId) {
        Channel channel = requireChannel(channelId);
        Object configured = channel.getMetadata() == null
                ? null
                : channel.getMetadata().get("shopifyLocationId");
        if (configured != null && !configured.toString().isBlank()) {
            return toLocationGid(configured.toString());
        }

        String domain = shopDomain(channel);
        String locationId = tokenService.execute(channelId, token -> {
            Map<String, Object> response = apiClient.executeGraphQl(
                    domain,
                    token.accessToken(),
                    "query { locations(first: 1) { nodes { id } } }",
                    Map.of()
            );
            ensureNoGraphQlErrors(response);
            Map<String, Object> data = map(response.get("data"));
            Map<String, Object> locations = map(data.get("locations"));
            List<Map<String, Object>> nodes = list(locations.get("nodes"));
            if (nodes.isEmpty() || nodes.get(0).get("id") == null) {
                throw new PlatformInventoryException(
                        "No Shopify location is available for inventory sync.", false);
            }
            return nodes.get(0).get("id").toString();
        });

        Map<String, Object> metadata = channel.getMetadata() == null
                ? new HashMap<>()
                : new HashMap<>(channel.getMetadata());
        metadata.put("shopifyLocationId", locationId);
        channel.setMetadata(metadata);
        channelRepository.save(channel);
        return locationId;
    }

    private void readBatch(
            String domain,
            String accessToken,
            List<String> inventoryItemIds,
            Map<InventoryLocationKey, Integer> result
    ) {
        String query = """
                query inventoryAvailable($ids: [ID!]!) {
                  nodes(ids: $ids) {
                    ... on InventoryItem {
                      id
                      inventoryLevels(first: 100) {
                        nodes {
                          location { id }
                          quantities(names: ["available"]) { name quantity }
                        }
                      }
                    }
                  }
                }
                """;
        Map<String, Object> response = apiClient.executeGraphQl(
                domain, accessToken, query, Map.of("ids", inventoryItemIds));
        ensureNoGraphQlErrors(response);
        for (Map<String, Object> node : list(map(response.get("data")).get("nodes"))) {
            if (node == null || node.get("id") == null) {
                continue;
            }
            String itemId = node.get("id").toString();
            for (Map<String, Object> level : list(map(node.get("inventoryLevels")).get("nodes"))) {
                String locationId = text(map(level.get("location")).get("id"));
                Integer available = quantity(list(level.get("quantities")), "available");
                if (locationId != null && available != null) {
                    result.put(new InventoryLocationKey(itemId, locationId), available);
                }
            }
        }
    }

    private void writeBatch(
            UUID channelId,
            String domain,
            String accessToken,
            List<ShopifyInventorySetCommand> commands,
            String idempotencyKey
    ) {
        String mutation = """
                mutation inventorySetQuantities($input: InventorySetQuantitiesInput!, $idempotencyKey: String!) {
                  inventorySetQuantities(input: $input) @idempotent(key: $idempotencyKey) {
                    inventoryAdjustmentGroup { changes { name delta quantityAfterChange } }
                    userErrors { code field message }
                  }
                }
                """;
        List<Map<String, Object>> quantities = new ArrayList<>();
        for (ShopifyInventorySetCommand command : commands) {
            Map<String, Object> quantity = new HashMap<>();
            quantity.put("inventoryItemId", toInventoryItemGid(command.key().inventoryItemId()));
            quantity.put("locationId", toLocationGid(command.key().locationId()));
            quantity.put("quantity", Math.max(command.targetAvailable(), 0));
            quantity.put("changeFromQuantity", command.changeFromQuantity());
            quantities.add(quantity);
        }
        Map<String, Object> input = new HashMap<>();
        input.put("name", "available");
        input.put("reason", "correction");
        input.put("referenceDocumentUri",
                "omnisales://channels/" + channelId + "/inventory-sync/" + idempotencyKey);
        input.put("quantities", quantities);

        Map<String, Object> response = apiClient.executeGraphQl(
                domain, accessToken, mutation,
                Map.of("input", input, "idempotencyKey", idempotencyKey));
        ensureNoGraphQlErrors(response);
        List<Map<String, Object>> errors = list(
                map(map(response.get("data")).get("inventorySetQuantities")).get("userErrors"));
        if (!errors.isEmpty()) {
            boolean casConflict = errors.stream()
                    .map(error -> (text(error.get("code")) + " " + text(error.get("message"))).toLowerCase())
                    .anyMatch(message -> message.contains("compare")
                            || message.contains("changefrom")
                            || message.contains("stale"));
            throw new PlatformInventoryException(
                    "Shopify inventorySetQuantities failed: " + errors, !casConflict);
        }
    }

    private void ensureStocked(
            String domain,
            String accessToken,
            List<ShopifyInventorySetCommand> commands
    ) {
        List<ShopifyInventorySetCommand> activationCandidates = commands.stream()
                .filter(command -> command.changeFromQuantity() == null)
                .toList();
        if (activationCandidates.isEmpty()) {
            return;
        }
        Map<InventoryLocationKey, Integer> existing = new HashMap<>();
        readBatch(domain, accessToken,
                activationCandidates.stream()
                        .map(command -> toInventoryItemGid(command.key().inventoryItemId()))
                        .distinct().toList(),
                existing);
        for (ShopifyInventorySetCommand command : activationCandidates) {
            InventoryLocationKey key = new InventoryLocationKey(
                    toInventoryItemGid(command.key().inventoryItemId()),
                    toLocationGid(command.key().locationId()));
            if (!existing.containsKey(key)) {
                activate(domain, accessToken, key, command.targetAvailable());
            }
        }
    }

    private void activate(
            String domain,
            String accessToken,
            InventoryLocationKey key,
            int available
    ) {
        String mutation = """
                mutation inventoryActivate($inventoryItemId: ID!, $locationId: ID!, $available: Int, $idempotencyKey: String!) {
                  inventoryActivate(inventoryItemId: $inventoryItemId, locationId: $locationId, available: $available)
                  @idempotent(key: $idempotencyKey) {
                    userErrors { field message }
                  }
                }
                """;
        Map<String, Object> response = apiClient.executeGraphQl(
                domain,
                accessToken,
                mutation,
                Map.of(
                        "inventoryItemId", key.inventoryItemId(),
                        "locationId", key.locationId(),
                        "available", Math.max(available, 0),
                        "idempotencyKey", UUID.randomUUID().toString()
                )
        );
        ensureNoGraphQlErrors(response);
        List<Map<String, Object>> errors = list(
                map(map(response.get("data")).get("inventoryActivate")).get("userErrors"));
        boolean onlyAlreadyActive = !errors.isEmpty() && errors.stream()
                .map(error -> text(error.get("message")))
                .allMatch(message -> message != null
                        && message.toLowerCase().contains("already"));
        if (!errors.isEmpty() && !onlyAlreadyActive) {
            throw new PlatformInventoryException("Shopify inventoryActivate failed: " + errors, false);
        }
    }

    private Channel requireChannel(UUID channelId) {
        return channelRepository.findById(channelId)
                .filter(channel -> channel.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.CHANNEL_NOT_FOUND));
    }

    private String shopDomain(Channel channel) {
        Object raw = channel.getMetadata() == null ? null : channel.getMetadata().get("shopDomain");
        return domainNormalizer.normalizeHandle(raw == null ? channel.getDisplayName() : raw.toString());
    }

    private void ensureNoGraphQlErrors(Map<String, Object> response) {
        if (response != null && response.get("errors") != null) {
            String message = response.get("errors").toString();
            throw new PlatformInventoryException(
                    "Shopify GraphQL failed: " + message,
                    isTransient(message)
            );
        }
    }

    private boolean isTransient(String message) {
        String normalized = message == null ? "" : message.toLowerCase();
        return normalized.contains("429")
                || normalized.contains("throttl")
                || normalized.contains("timeout")
                || normalized.contains("internal")
                || normalized.contains("temporar")
                || normalized.contains("500")
                || normalized.contains("502")
                || normalized.contains("503")
                || normalized.contains("504");
    }

    private String toInventoryItemGid(String value) {
        return value.startsWith("gid://shopify/InventoryItem/")
                ? value
                : "gid://shopify/InventoryItem/" + value;
    }

    private String toLocationGid(String value) {
        return value.startsWith("gid://shopify/Location/")
                ? value
                : "gid://shopify/Location/" + value;
    }

    private Integer quantity(List<Map<String, Object>> quantities, String name) {
        for (Map<String, Object> quantity : quantities) {
            if (name.equals(text(quantity.get("name"))) && quantity.get("quantity") != null) {
                Object value = quantity.get("quantity");
                return value instanceof Number number
                        ? number.intValue()
                        : Integer.parseInt(value.toString());
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> source ? (Map<String, Object>) source : Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> list(Object value) {
        return value instanceof List<?> source ? (List<Map<String, Object>>) source : List.of();
    }

    private String text(Object value) {
        return value == null ? null : value.toString();
    }
}
