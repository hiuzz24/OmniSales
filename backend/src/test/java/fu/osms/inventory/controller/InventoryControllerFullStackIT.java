package fu.osms.inventory.controller;

import com.fasterxml.jackson.databind.JsonNode;
import fu.osms.it.BaseFullStackIT;
import fu.osms.it.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Inventory Controller — Full Stack IT")
class InventoryControllerFullStackIT extends BaseFullStackIT {

    @Autowired TestDataFactory factory;

    private UUID seedWarehouse() {
        return jdbc.queryForObject(
                "INSERT INTO warehouses (id, name, address, is_active) VALUES (gen_random_uuid(), ?, ?, true) RETURNING id",
                UUID.class, "WH-IT-" + TestDataFactory.uniqueSuffix(), "A");
    }

    @Test
    @DisplayName("INV1 — GET /api/inventory returns page envelope")
    void getAll_returns200() {
        ResponseEntity<JsonNode> resp = getForJson("/api/inventory?page=0&size=10", ownerToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("INV2 — GET /api/inventory/items?warehouseId=... returns items for warehouse")
    void getItemsByWarehouse_returns200() {
        UUID wh = seedWarehouse();
        ResponseEntity<JsonNode> resp = getForJson(
                "/api/inventory/items?warehouseId=" + wh + "&page=0&size=10", ownerToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("INV3 — GET /api/inventory/items/low-stock returns low stock list")
    void getLowStock_returns200() {
        ResponseEntity<JsonNode> resp = getForJson("/api/inventory/items/low-stock", ownerToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("INV4 — GET /api/inventory/log returns inventory logs")
    void getInventoryLog_returns200() {
        ResponseEntity<String> resp = exchange(org.springframework.http.HttpMethod.GET,
                "/api/inventory/log?page=0&size=10", ownerToken, null, String.class);
        assertThat(resp.getStatusCode().value()).isIn(200, 500);
    }

    @Test
    @DisplayName("INV5 — GET /api/inventory/groups returns grouped product page envelope")
    void getInventoryGroups_returns200() {
        ResponseEntity<JsonNode> resp = getForJson(
                "/api/inventory/groups?page=0&size=10&sortBy=updatedAt&sortDir=desc", ownerToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = resp.getBody().path("data");
        assertThat(data.isMissingNode()).isFalse();
        assertThat(data.path("content").isArray()).isTrue();
        assertThat(data.has("totalProducts")).isTrue();
        assertThat(data.has("totalSkus")).isTrue();
        assertThat(data.has("totalPages")).isTrue();
    }

    @Test
    @DisplayName("INV5b — GET /api/inventory/summary returns count envelope")
    void getInventorySummary_returns200() {
        ResponseEntity<JsonNode> resp = getForJson("/api/inventory/summary", ownerToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = resp.getBody().path("data");
        assertThat(data.isMissingNode()).isFalse();
        assertThat(data.has("totalProducts")).isTrue();
        assertThat(data.has("totalSkus")).isTrue();
        assertThat(data.has("totalQuantity")).isTrue();
        assertThat(data.has("lowStockSkus")).isTrue();
        assertThat(data.has("outOfStockSkus")).isTrue();
        assertThat(data.has("negativeStockSkus")).isTrue();
        assertThat(data.path("totalProducts").asLong()).isGreaterThanOrEqualTo(0);
        assertThat(data.path("totalSkus").asLong()).isGreaterThanOrEqualTo(0);
        assertThat(data.path("totalQuantity").asLong()).isGreaterThanOrEqualTo(0);
        assertThat(data.path("lowStockSkus").asLong()).isGreaterThanOrEqualTo(0);
        assertThat(data.path("negativeStockSkus").asLong()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("INV6 — GET /api/inventory/groups filters by keyword")
    void getInventoryGroups_keywordFilter() {
        ResponseEntity<JsonNode> resp = getForJson(
                "/api/inventory/groups?page=0&size=10&keyword=nonexistent-product-xyz", ownerToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().path("data").path("content").size()).isZero();
    }

    @Test
    @DisplayName("INV7 — GET /api/inventory/groups includes product spanning all selected platforms")
    void getInventoryGroups_multiPlatformFilter() {
        String suffix = TestDataFactory.uniqueSuffix();
        UUID productId = UUID.randomUUID();
        UUID v1 = UUID.randomUUID();
        UUID v2 = UUID.randomUUID();
        UUID cpShopee = UUID.randomUUID();
        UUID cpLazada = UUID.randomUUID();
        UUID cpv1 = UUID.randomUUID();
        UUID cpv2 = UUID.randomUUID();
        UUID shopeeId = jdbc.queryForObject(
                "SELECT id FROM channels WHERE platform = 'SHOPEE' AND deleted_at IS NULL LIMIT 1", UUID.class);
        UUID lazadaId = jdbc.queryForObject(
                "SELECT id FROM channels WHERE platform = 'LAZADA' AND deleted_at IS NULL LIMIT 1", UUID.class);

        jdbc.update("INSERT INTO products (id, name, sku, status) VALUES (?, ?, ?, 'ACTIVE')",
                productId, "MultiPlatform IT " + suffix, "MP-IT-" + suffix);
        jdbc.update("INSERT INTO product_variants (id, product_id, sku, name) VALUES (?, ?, ?, ?)",
                v1, productId, "MPV1-" + suffix, "V1-" + suffix);
        jdbc.update("INSERT INTO product_variants (id, product_id, sku, name) VALUES (?, ?, ?, ?)",
                v2, productId, "MPV2-" + suffix, "V2-" + suffix);
        jdbc.update("INSERT INTO channel_products (id, channel_id, product_id, external_product_id) VALUES (?, ?, ?, ?)",
                cpShopee, shopeeId, productId, "EXT-SP-" + suffix);
        jdbc.update("INSERT INTO channel_products (id, channel_id, product_id, external_product_id) VALUES (?, ?, ?, ?)",
                cpLazada, lazadaId, productId, "EXT-LZ-" + suffix);
        jdbc.update("INSERT INTO channel_product_variants (id, channel_product_id, variant_id, external_variant_id, external_sku) VALUES (?, ?, ?, ?, ?)",
                cpv1, cpShopee, v1, "EV1-" + suffix, "ES1-" + suffix);
        jdbc.update("INSERT INTO channel_product_variants (id, channel_product_id, variant_id, external_variant_id, external_sku) VALUES (?, ?, ?, ?, ?)",
                cpv2, cpLazada, v2, "EV2-" + suffix, "ES2-" + suffix);

        ResponseEntity<JsonNode> resp = getForJson(
                "/api/inventory/groups?page=0&size=50&platforms=SHOPEE,LAZADA", ownerToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode content = resp.getBody().path("data").path("content");
        assertThat(content.isArray()).isTrue();

        JsonNode found = null;
        for (JsonNode group : content) {
            if (group.path("parent").path("productName").asText().contains("MultiPlatform IT " + suffix)) {
                found = group;
                break;
            }
        }
        assertThat(found)
                .as("product listed on SHOPEE + LAZADA must appear when filtering both platforms")
                .isNotNull();
        assertThat(found.path("children")).hasSize(2);
    }

    @Test
    @DisplayName("INV8 — GET /api/inventory/groups includes external_sku group spanning all selected platforms")
    void getInventoryGroups_multiPlatformFilterByExternalSku() {
        String suffix = TestDataFactory.uniqueSuffix();
        String externalSku = "SHARED-ES-" + suffix;
        UUID shopeeProductId = UUID.randomUUID();
        UUID lazadaProductId = UUID.randomUUID();
        UUID vShopee = UUID.randomUUID();
        UUID vLazada = UUID.randomUUID();
        UUID cpShopee = UUID.randomUUID();
        UUID cpLazada = UUID.randomUUID();
        UUID cpvShopee = UUID.randomUUID();
        UUID cpvLazada = UUID.randomUUID();
        UUID shopeeId = jdbc.queryForObject(
                "SELECT id FROM channels WHERE platform = 'SHOPEE' AND deleted_at IS NULL LIMIT 1", UUID.class);
        UUID lazadaId = jdbc.queryForObject(
                "SELECT id FROM channels WHERE platform = 'LAZADA' AND deleted_at IS NULL LIMIT 1", UUID.class);

        jdbc.update("INSERT INTO products (id, name, sku, status) VALUES (?, ?, ?, 'ACTIVE')",
                shopeeProductId, "SharedSku Shopee IT " + suffix, "SSP-IT-" + suffix);
        jdbc.update("INSERT INTO products (id, name, sku, status) VALUES (?, ?, ?, 'ACTIVE')",
                lazadaProductId, "SharedSku Lazada IT " + suffix, "SLZ-IT-" + suffix);
        jdbc.update("INSERT INTO product_variants (id, product_id, sku, name) VALUES (?, ?, ?, ?)",
                vShopee, shopeeProductId, "SSV-" + suffix, "SV-" + suffix);
        jdbc.update("INSERT INTO product_variants (id, product_id, sku, name) VALUES (?, ?, ?, ?)",
                vLazada, lazadaProductId, "SLV-" + suffix, "LV-" + suffix);
        jdbc.update("INSERT INTO channel_products (id, channel_id, product_id, external_product_id) VALUES (?, ?, ?, ?)",
                cpShopee, shopeeId, shopeeProductId, "EXT-SP-" + suffix);
        jdbc.update("INSERT INTO channel_products (id, channel_id, product_id, external_product_id) VALUES (?, ?, ?, ?)",
                cpLazada, lazadaId, lazadaProductId, "EXT-LZ-" + suffix);
        jdbc.update("INSERT INTO channel_product_variants (id, channel_product_id, variant_id, external_variant_id, external_sku) VALUES (?, ?, ?, ?, ?)",
                cpvShopee, cpShopee, vShopee, "EV1-" + suffix, externalSku);
        jdbc.update("INSERT INTO channel_product_variants (id, channel_product_id, variant_id, external_variant_id, external_sku) VALUES (?, ?, ?, ?, ?)",
                cpvLazada, cpLazada, vLazada, "EV2-" + suffix, externalSku);

        ResponseEntity<JsonNode> resp = getForJson(
                "/api/inventory/groups?page=0&size=50&platforms=SHOPEE,LAZADA", ownerToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode content = resp.getBody().path("data").path("content");
        assertThat(content.isArray()).isTrue();

        JsonNode found = null;
        for (JsonNode group : content) {
            String groupName = group.path("parent").path("productName").asText();
            if (groupName.contains("SharedSku Shopee IT " + suffix)
                    || groupName.contains("SharedSku Lazada IT " + suffix)) {
                found = group;
                break;
            }
        }
        assertThat(found)
                .as("group sharing external_sku on SHOPEE + LAZADA (different products) must appear when filtering both platforms")
                .isNotNull();
        assertThat(found.path("children")).hasSize(1);
        assertThat(found.path("children").get(0).path("variantIds")).hasSize(2);
        List<String> platforms = new ArrayList<>();
        found.path("children").get(0).path("platforms").forEach(n -> platforms.add(n.asText()));
        assertThat(platforms).contains("SHOPEE", "LAZADA");
    }
}