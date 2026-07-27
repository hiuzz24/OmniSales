package fu.osms.sync.lazada.inventory.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.sync.inventory.PlatformInventoryException;
import fu.osms.sync.lazada.inventory.LazadaInventoryGateway;
import fu.osms.sync.lazada.inventory.LazadaInventoryKey;
import fu.osms.sync.lazada.inventory.LazadaInventorySetCommand;
import fu.osms.sync.lazada.service.LazadaAuthorizedApiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LazadaInventoryGatewayImpl implements LazadaInventoryGateway {

    private static final String READ_PATH = "/products/get";
    private static final String WRITE_PATH = "/product/stock/sellable/update";
    private static final int PAGE_SIZE = 50;

    private final LazadaAuthorizedApiClient apiClient;
    private final ObjectMapper objectMapper;

    @Override
    public Map<LazadaInventoryKey, Integer> readSellable(
            UUID channelId,
            Collection<LazadaInventoryKey> keys
    ) {
        if (keys == null || keys.isEmpty()) {
            return Map.of();
        }
        Set<String> wantedItems = keys.stream()
                .map(LazadaInventoryKey::itemId)
                .filter(this::hasText)
                .collect(Collectors.toSet());
        Map<LazadaInventoryKey, Integer> result = new LinkedHashMap<>();
        int offset = 0;
        while (result.size() < keys.size()) {
            String response = apiClient.executeGet(channelId, READ_PATH, Map.of(
                    "filter", "all",
                    "limit", String.valueOf(PAGE_SIZE),
                    "offset", String.valueOf(offset),
                    "options", "1"
            ));
            JsonNode root = readTree(response, READ_PATH);
            ensureSuccess(root, READ_PATH);
            List<JsonNode> products = array(first(root,
                    "/data/products", "/data/product", "/products"));
            for (JsonNode product : products) {
                String itemId = text(product, "item_id", "itemId", "id");
                if (!wantedItems.isEmpty() && !wantedItems.contains(itemId)) {
                    continue;
                }
                collectProductQuantities(product, itemId, keys, result);
            }
            if (products.size() < PAGE_SIZE) {
                break;
            }
            offset += products.size();
        }
        return result;
    }

    @Override
    public int updateSellable(
            UUID channelId,
            Collection<LazadaInventorySetCommand> commands
    ) {
        if (commands == null || commands.isEmpty()) {
            return 0;
        }
        List<LazadaInventorySetCommand> commandList = List.copyOf(commands);
        for (int start = 0; start < commandList.size(); start += PAGE_SIZE) {
            List<LazadaInventorySetCommand> batch = commandList.subList(
                    start, Math.min(start + PAGE_SIZE, commandList.size()));
            String payload = "<Request><Product><Skus>"
                    + batch.stream().map(this::skuXml).collect(Collectors.joining())
                    + "</Skus></Product></Request>";
            String response = apiClient.executePost(
                    channelId, WRITE_PATH, Map.of("payload", payload));
            ensureSuccess(readTree(response, WRITE_PATH), WRITE_PATH);
        }
        return commandList.size();
    }

    private void collectProductQuantities(
            JsonNode product,
            String itemId,
            Collection<LazadaInventoryKey> keys,
            Map<LazadaInventoryKey, Integer> result
    ) {
        JsonNode skus = first(product, "/skus", "/Skus", "/sku_list", "/Sku");
        for (JsonNode sku : array(skus)) {
            String skuId = text(sku, "sku_id", "SkuId", "id");
            String sellerSku = text(sku, "seller_sku", "SellerSku", "sku");
            for (LazadaInventoryKey key : keys) {
                if (!matches(key, itemId, skuId, sellerSku)) {
                    continue;
                }
                Integer quantity = sellableForWarehouse(sku, key.warehouseCode());
                if (quantity != null) {
                    result.put(key, quantity);
                }
            }
        }
    }

    private Integer sellableForWarehouse(JsonNode sku, String warehouseCode) {
        JsonNode warehouses = first(sku,
                "/multiWarehouseInventories", "/warehouseInventories",
                "/channelInventories", "/warehouses");
        for (JsonNode warehouse : array(warehouses)) {
            String code = text(warehouse,
                    "warehouseCode", "warehouse_code", "code", "id");
            if (!hasText(warehouseCode) || warehouseCode.equals(code)) {
                return integer(warehouse,
                        "sellableQuantity", "sellable_quantity",
                        "availableQuantity", "available_quantity", "available");
            }
        }
        return integer(sku,
                "sellableQuantity", "sellable_quantity",
                "availableQuantity", "available_quantity", "available");
    }

    private boolean matches(
            LazadaInventoryKey key,
            String itemId,
            String skuId,
            String sellerSku
    ) {
        if (hasText(key.itemId()) && !key.itemId().equals(itemId)) {
            return false;
        }
        if (hasText(key.skuId())) {
            return key.skuId().equals(skuId);
        }
        return hasText(key.sellerSku()) && key.sellerSku().equals(sellerSku);
    }

    private String skuXml(LazadaInventorySetCommand command) {
        LazadaInventoryKey key = command.key();
        StringBuilder xml = new StringBuilder("<Sku>")
                .append("<ItemId>").append(escape(key.itemId())).append("</ItemId>")
                .append("<SkuId>").append(escape(key.skuId())).append("</SkuId>");
        if (hasText(key.sellerSku())) {
            xml.append("<SellerSku>").append(escape(key.sellerSku())).append("</SellerSku>");
        }
        if (hasText(key.warehouseCode())) {
            xml.append("<MultiWarehouseInventories><MultiWarehouseInventory>")
                    .append("<WarehouseCode>").append(escape(key.warehouseCode())).append("</WarehouseCode>")
                    .append("<SellableQuantity>").append(Math.max(command.targetSellable(), 0))
                    .append("</SellableQuantity>")
                    .append("</MultiWarehouseInventory></MultiWarehouseInventories>");
        } else {
            xml.append("<SellableQuantity>").append(Math.max(command.targetSellable(), 0))
                    .append("</SellableQuantity>");
        }
        return xml.append("</Sku>").toString();
    }

    private void ensureSuccess(JsonNode root, String path) {
        String code = root.path("code").asText("");
        if (!code.isBlank() && !"0".equals(code)) {
            String message = firstText(
                    root.path("message").asText(null),
                    root.path("msg").asText(null),
                    root.path("error_msg").asText(null),
                    root.toString()
            );
            throw new PlatformInventoryException(
                    "Lazada API " + path + " failed: " + message,
                    isTransient(code + " " + message)
            );
        }
    }

    private JsonNode readTree(String response, String path) {
        try {
            return objectMapper.readTree(response);
        } catch (Exception error) {
            throw new PlatformInventoryException(
                    "Cannot parse Lazada response for " + path, false, error);
        }
    }

    private boolean isTransient(String message) {
        String value = message == null ? "" : message.toLowerCase();
        return value.contains("e006")
                || value.contains("429")
                || value.contains("limit")
                || value.contains("timeout")
                || value.contains("internal")
                || value.contains("temporar")
                || value.contains("500")
                || value.contains("502")
                || value.contains("503")
                || value.contains("504");
    }

    private JsonNode first(JsonNode root, String... pointers) {
        for (String pointer : pointers) {
            JsonNode value = root.at(pointer);
            if (!value.isMissingNode() && !value.isNull()) {
                return value;
            }
        }
        return objectMapper.createArrayNode();
    }

    private List<JsonNode> array(JsonNode value) {
        List<JsonNode> result = new ArrayList<>();
        if (value != null && value.isArray()) {
            value.forEach(result::add);
        } else if (value != null && value.isObject()) {
            value.elements().forEachRemaining(result::add);
        }
        return result;
    }

    private String text(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull() && !value.asText("").isBlank()) {
                return value.asText();
            }
        }
        return null;
    }

    private Integer integer(JsonNode node, String... fields) {
        String value = text(node, fields);
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
