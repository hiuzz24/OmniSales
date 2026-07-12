package fu.osms.inventory.service;

import com.fasterxml.jackson.databind.JsonNode;
import fu.osms.inventory.entity.InventoryItem;
import fu.osms.it.BaseFullStackIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-stack integration tests for SC-12 Low-stock Alerts.
 *
 * <p>Drives {@link InventoryAlertService#notifyLowStockAfterStockChange} directly
 * to verify the alert behavior end-to-end (DB write + HTTP GET visible to OWNER).</p>
 */
@DisplayName("Low-Stock Alert Flow — Full Stack IT")
class LowStockAlertIT extends BaseFullStackIT {

    @Autowired
    private InventoryAlertService inventoryAlertService;

    private HttpHeaders jsonHeaders(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setAccept(List.of(MediaType.APPLICATION_JSON));
        h.setBearerAuth(token);
        return h;
    }

    private UUID seedProductVariant(String skuPrefix) {
        UUID productId = seedProduct();
        UUID variantId = UUID.randomUUID();
        jdbc.update("INSERT INTO product_variants (id, product_id, sku, name, is_active, option_values) " +
                        "VALUES (?, ?, ?, ?, TRUE, '{}'::jsonb)",
                variantId, productId, skuPrefix + "-" + UUID.randomUUID().toString().substring(0, 8), "Test Variant");
        return variantId;
    }

    private UUID seedProduct() {
        UUID productId = UUID.randomUUID();
        UUID categoryId = jdbc.queryForObject("SELECT id FROM categories LIMIT 1", UUID.class);
        jdbc.update("INSERT INTO products (id, category_id, sku, name, brand, status, attributes, version) " +
                        "VALUES (?, ?, ?, ?, 'Brand', 'ACTIVE', '{}'::jsonb, 0)",
                productId, categoryId, "PROD-" + UUID.randomUUID().toString().substring(0, 8),
                "Test Product " + UUID.randomUUID().toString().substring(0, 6));
        return productId;
    }

    private UUID seedWarehouse() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO warehouses (id, name, address, is_active) VALUES (?, ?, 'addr', TRUE)",
                id, "WH-" + UUID.randomUUID().toString().substring(0, 6));
        return id;
    }

    private UUID seedInventoryItem(UUID warehouseId, UUID variantId, int qty, int reserved, int threshold) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO inventory_items (warehouse_id, variant_id, quantity_on_hand, reserved_quantity, low_stock_threshold, version) " +
                        "VALUES (?, ?, ?, ?, ?, 0)",
                warehouseId, variantId, qty, reserved, threshold);
        return id;
    }

    private long countLowStockNotificationsForOwner() {
        ResponseEntity<JsonNode> resp = rest.exchange(
                "/api/notifications?unreadOnly=true&page=0&size=200",
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders(ownerToken)),
                JsonNode.class);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode content = resp.getBody().path("data").path("content");
        long count = 0;
        for (JsonNode node : content) {
            if ("LOW_STOCK".equals(node.path("type").asText())) {
                count++;
            }
        }
        return count;
    }

    private InventoryItem fetchInventoryItem(UUID itemId) {
        return jdbc.query("SELECT id, quantity_on_hand, reserved_quantity, low_stock_threshold FROM inventory_items WHERE id = ?",
                (rs, n) -> InventoryItem.builder()
                        .id((UUID) rs.getObject("id"))
                        .quantityOnHand(rs.getInt("quantity_on_hand"))
                        .reservedQuantity(rs.getInt("reserved_quantity"))
                        .lowStockThreshold(rs.getInt("low_stock_threshold"))
                        .build(), itemId)
                .stream().findFirst().orElseThrow();
    }

    @Test
    @DisplayName("LS-1 — Lower stock below threshold → notification LOW_STOCK appears")
    void lowerStock_emitsLowStockNotification() {
        long baseline = countLowStockNotificationsForOwner();

        UUID warehouseId = seedWarehouse();
        UUID variantId = seedProductVariant("SKU-LS-1");
        UUID itemId = seedInventoryItem(warehouseId, variantId, 20, 0, 5);

        jdbc.update("UPDATE inventory_items SET quantity_on_hand = 3 WHERE id = ?", itemId);
        InventoryItem item = fetchInventoryItem(itemId);

        inventoryAlertService.notifyLowStockAfterStockChange(item);

        long after = countLowStockNotificationsForOwner();
        assertThat(after).isGreaterThan(baseline);
    }

    @Test
    @DisplayName("LS-2 — Stock = 0 → notification title contains 'hết hàng'")
    void zeroStock_emitsOutOfStockNotification() {
        long baseline = countLowStockNotificationsForOwner();

        UUID warehouseId = seedWarehouse();
        UUID variantId = seedProductVariant("SKU-LS-2");
        UUID itemId = seedInventoryItem(warehouseId, variantId, 5, 5, 5);

        InventoryItem item = fetchInventoryItem(itemId);

        inventoryAlertService.notifyLowStockAfterStockChange(item);

        ResponseEntity<JsonNode> resp = rest.exchange(
                "/api/notifications?unreadOnly=true&page=0&size=200",
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders(ownerToken)),
                JsonNode.class);
        JsonNode content = resp.getBody().path("data").path("content");
        boolean foundOutOfStock = false;
        for (JsonNode node : content) {
            if ("LOW_STOCK".equals(node.path("type").asText())
                    && node.path("title").asText().contains("hết hàng")) {
                foundOutOfStock = true;
                break;
            }
        }
        assertThat(foundOutOfStock).isTrue();
        assertThat(countLowStockNotificationsForOwner()).isGreaterThan(baseline);
    }

    @Test
    @DisplayName("LS-3 — Two rapid notifications in window are deduplicated (only first creates notification)")
    void rapidDrops_deduplicatedWithinWindow() {
        long baseline = countLowStockNotificationsForOwner();

        UUID warehouseId = seedWarehouse();
        UUID variantId = seedProductVariant("SKU-LS-3");
        UUID itemId = seedInventoryItem(warehouseId, variantId, 10, 0, 5);

        jdbc.update("UPDATE inventory_items SET quantity_on_hand = 4 WHERE id = ?", itemId);
        inventoryAlertService.notifyLowStockAfterStockChange(fetchInventoryItem(itemId));

        jdbc.update("UPDATE inventory_items SET quantity_on_hand = 3 WHERE id = ?", itemId);
        inventoryAlertService.notifyLowStockAfterStockChange(fetchInventoryItem(itemId));

        long after = countLowStockNotificationsForOwner();
        assertThat(after).isEqualTo(baseline + 1);
    }

    @Test
    @DisplayName("LS-4 — Stock above threshold → no new notification")
    void stockAboveThreshold_noNewNotification() {
        long baseline = countLowStockNotificationsForOwner();

        UUID warehouseId = seedWarehouse();
        UUID variantId = seedProductVariant("SKU-LS-4");
        UUID itemId = seedInventoryItem(warehouseId, variantId, 50, 0, 5);

        inventoryAlertService.notifyLowStockAfterStockChange(fetchInventoryItem(itemId));

        long after = countLowStockNotificationsForOwner();
        assertThat(after).isEqualTo(baseline);
    }

    @Test
    @DisplayName("LS-5 — sendLowStockReminders scheduled task fires and creates notifications for low items")
    void scheduledReminders_createNotifications() {
        long baseline = countLowStockNotificationsForOwner();

        UUID warehouseId = seedWarehouse();
        UUID variantId = seedProductVariant("SKU-LS-5");
        seedInventoryItem(warehouseId, variantId, 1, 0, 5);

        inventoryAlertService.sendLowStockReminders();

        long after = countLowStockNotificationsForOwner();
        assertThat(after).isGreaterThan(baseline);
    }
}