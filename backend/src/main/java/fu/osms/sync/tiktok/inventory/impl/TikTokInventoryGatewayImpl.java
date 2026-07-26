package fu.osms.sync.tiktok.inventory.impl;

import fu.osms.sync.inventory.PlatformInventoryException;
import fu.osms.sync.tiktok.TikTokAuthorizedApiClient;
import fu.osms.sync.tiktok.inventory.TikTokInventoryGateway;
import fu.osms.sync.tiktok.inventory.TikTokInventorySetCommand;
import fu.osms.sync.tiktok.inventory.TikTokInventoryTarget;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TikTokInventoryGatewayImpl implements TikTokInventoryGateway {

    private static final int SEARCH_BATCH_SIZE = 600;

    private final TikTokAuthorizedApiClient apiClient;

    @Override
    public Map<String, Integer> readAvailable(
            UUID channelId,
            String shopCipher,
            Collection<String> skuIds
    ) {
        List<String> requestedSkuIds = sanitize(skuIds);
        if (requestedSkuIds.isEmpty()) {
            return Map.of();
        }
        requireShopCipher(shopCipher);
        Map<String, Integer> result = new LinkedHashMap<>();
        try {
            for (int start = 0; start < requestedSkuIds.size(); start += SEARCH_BATCH_SIZE) {
                List<String> batch = requestedSkuIds.subList(
                        start, Math.min(start + SEARCH_BATCH_SIZE, requestedSkuIds.size()));
                collectInventory(
                        apiClient.searchInventoryBySkuIds(channelId, shopCipher, batch),
                        result
                );
            }
            return result;
        } catch (PlatformInventoryException error) {
            throw error;
        } catch (RuntimeException error) {
            throw platformError("Cannot read TikTok inventory", error);
        }
    }

    @Override
    public void setAvailable(
            UUID channelId,
            String shopCipher,
            Collection<TikTokInventorySetCommand> commands
    ) {
        if (commands == null || commands.isEmpty()) {
            return;
        }
        requireShopCipher(shopCipher);
        Map<String, List<TikTokInventorySetCommand>> byProduct = new LinkedHashMap<>();
        for (TikTokInventorySetCommand command : commands) {
            validate(command);
            byProduct.computeIfAbsent(command.target().productId(), ignored -> new ArrayList<>())
                    .add(command);
        }

        try {
            for (Map.Entry<String, List<TikTokInventorySetCommand>> entry : byProduct.entrySet()) {
                List<Map<String, Object>> skus = entry.getValue().stream()
                        .map(this::skuPayload)
                        .toList();
                apiClient.updateInventory(channelId, shopCipher, entry.getKey(), skus);
            }
        } catch (PlatformInventoryException error) {
            throw error;
        } catch (RuntimeException error) {
            throw platformError("Cannot update TikTok inventory", error);
        }
    }

    private void collectInventory(Map<String, Object> response, Map<String, Integer> result) {
        Map<String, Object> data = map(response.get("data"));
        for (Map<String, Object> product : list(data.get("inventory"))) {
            for (Map<String, Object> sku : list(product.get("skus"))) {
                String skuId = text(first(sku, "id", "sku_id"));
                Integer available = integer(sku.get("total_available_quantity"));
                if (hasText(skuId) && available != null) {
                    result.put(skuId, Math.max(available, 0));
                }
            }
        }
    }

    private Map<String, Object> skuPayload(TikTokInventorySetCommand command) {
        TikTokInventoryTarget target = command.target();
        List<Map<String, Object>> inventory = target.warehouseIds().stream()
                .map(warehouseId -> Map.<String, Object>of(
                        "warehouse_id", warehouseId,
                        "quantity", warehouseId.equals(target.primaryWarehouseId())
                                ? Math.max(command.targetAvailable(), 0)
                                : 0
                ))
                .toList();
        return Map.of("id", target.skuId(), "inventory", inventory);
    }

    private void validate(TikTokInventorySetCommand command) {
        if (command == null || command.target() == null) {
            throw new PlatformInventoryException("TikTok inventory command is missing", false);
        }
        TikTokInventoryTarget target = command.target();
        if (!hasText(target.productId()) || !hasText(target.skuId())) {
            throw new PlatformInventoryException(
                    "TikTok inventory command is missing product or SKU ID", false);
        }
        if (!hasText(target.primaryWarehouseId())
                || target.warehouseIds() == null
                || target.warehouseIds().isEmpty()
                || !target.warehouseIds().contains(target.primaryWarehouseId())) {
            throw new PlatformInventoryException(
                    "TikTok inventory command has no valid primary warehouse", false);
        }
    }

    private void requireShopCipher(String shopCipher) {
        if (!hasText(shopCipher)) {
            throw new PlatformInventoryException(
                    "TikTok channel is missing shopCipher metadata", false);
        }
    }

    private PlatformInventoryException platformError(String prefix, RuntimeException error) {
        String message = error.getMessage() == null
                ? error.getClass().getSimpleName()
                : error.getMessage();
        return new PlatformInventoryException(
                prefix + ": " + message,
                isTransient(message),
                error
        );
    }

    private boolean isTransient(String message) {
        String value = message == null ? "" : message.toLowerCase();
        return value.contains("429")
                || value.contains("rate limit")
                || value.contains("timeout")
                || value.contains("timed out")
                || value.contains("temporar")
                || value.contains("unavailable")
                || value.contains("cannot call")
                || value.contains("500")
                || value.contains("502")
                || value.contains("503")
                || value.contains("504");
    }

    private List<String> sanitize(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(this::hasText)
                .distinct()
                .toList();
    }

    private Object first(Map<String, Object> values, String... keys) {
        for (String key : keys) {
            Object value = values.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> source
                ? (Map<String, Object>) source
                : Map.of();
    }

    private List<Map<String, Object>> list(Object value) {
        if (!(value instanceof List<?> source)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : source) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> copy = new LinkedHashMap<>();
                map.forEach((key, mapValue) -> copy.put(String.valueOf(key), mapValue));
                result.add(copy);
            }
        }
        return result;
    }

    private Integer integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? null : Integer.parseInt(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String text(Object value) {
        return value == null ? null : value.toString();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank() && !"null".equalsIgnoreCase(value);
    }
}
