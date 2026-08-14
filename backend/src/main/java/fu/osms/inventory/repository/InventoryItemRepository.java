package fu.osms.inventory.repository;

import fu.osms.common.enums.PlatformType;
import fu.osms.inventory.entity.InventoryItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID> {

    Page<InventoryItem> findByWarehouseId(UUID warehouseId, Pageable pageable);

    List<InventoryItem> findByWarehouseId(UUID warehouseId);

    @Query("SELECT DISTINCT i FROM InventoryItem i " +
            "JOIN FETCH i.variant v " +
            "LEFT JOIN FETCH v.product " +
            "WHERE i.warehouse.id = :warehouseId " +
            "AND EXISTS (" +
            "    SELECT 1 FROM ChannelProductVariant cpv " +
            "    WHERE cpv.variant = v " +
            "    AND cpv.channelProduct.channel.platform = :platform " +
            "    AND cpv.channelProduct.channel.deletedAt IS NULL " +
            "    AND cpv.channelProduct.mappingState = 'ACTIVE'" +
            ") " +
            "ORDER BY v.sku ASC")
    List<InventoryItem> findByWarehouseIdAndMappedPlatform(
            @Param("warehouseId") UUID warehouseId,
            @Param("platform") PlatformType platform);

    Optional<InventoryItem> findByWarehouseIdAndVariantId(UUID warehouseId, UUID variantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM InventoryItem i WHERE i.warehouse.id = :warehouseId AND i.variant.id = :variantId")
    Optional<InventoryItem> findByWarehouseIdAndVariantIdWithLock(@Param("warehouseId") UUID warehouseId,
                                                                   @Param("variantId") UUID variantId);

    @Query("SELECT DISTINCT i FROM InventoryItem i " +
            "JOIN FETCH i.warehouse " +
            "JOIN FETCH i.variant v " +
            "LEFT JOIN FETCH v.product " +
            "WHERE i.availableQuantity <= i.lowStockThreshold " +
            "ORDER BY i.availableQuantity ASC, i.updatedAt DESC")
    List<InventoryItem> findLowStockItems();

    List<InventoryItem> findByVariantIdIn(Collection<UUID> variantIds);

    @Query("SELECT i FROM InventoryItem i " +
            "JOIN FETCH i.variant v " +
            "WHERE i.warehouse.id = :warehouseId " +
            "AND v.id IN :variantIds")
    List<InventoryItem> findByWarehouseIdAndVariantIdIn(
            @Param("warehouseId") UUID warehouseId,
            @Param("variantIds") Collection<UUID> variantIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM InventoryItem i " +
            "JOIN FETCH i.warehouse " +
            "JOIN FETCH i.variant v " +
            "LEFT JOIN FETCH v.product " +
            "WHERE v.id = :variantId " +
            "ORDER BY i.updatedAt ASC")
    List<InventoryItem> findByVariantIdWithLock(@Param("variantId") UUID variantId);

    @Query(value = "SELECT i FROM InventoryItem i " +
            "LEFT JOIN FETCH i.warehouse " +
            "LEFT JOIN FETCH i.variant",
            countQuery = "SELECT COUNT(i) FROM InventoryItem i")
    Page<InventoryItem> findAllWithVariantRelationships(Pageable pageable);

    @Query(value = "SELECT DISTINCT i FROM InventoryItem i " +
            "LEFT JOIN FETCH i.warehouse " +
            "LEFT JOIN FETCH i.variant v " +
            "LEFT JOIN FETCH v.product p " +
            "LEFT JOIN FETCH p.category c " +
            "WHERE (:channelId IS NULL OR EXISTS (" +
            "    SELECT 1 FROM ChannelProductVariant cpv " +
            "    WHERE cpv.variant = v " +
            "    AND cpv.channelProduct.channel.id = :channelId " +
            "    AND cpv.channelProduct.channel.deletedAt IS NULL " +
            "    AND cpv.channelProduct.mappingState = 'ACTIVE'" +
            ")) " +
            "AND (:localOnly = false OR NOT EXISTS (" +
            "    SELECT 1 FROM ChannelProductVariant cpvLocal " +
            "    WHERE cpvLocal.variant = v " +
            "    AND cpvLocal.channelProduct.channel.deletedAt IS NULL " +
            "    AND cpvLocal.channelProduct.mappingState = 'ACTIVE'" +
            ")) " +
            "AND (:warehouseId IS NULL OR i.warehouse.id = :warehouseId) " +
            "AND (:keyword IS NULL OR LOWER(v.sku) LIKE :keyword OR LOWER(v.name) LIKE :keyword OR LOWER(p.name) LIKE :keyword) " +
            "AND (:status IS NULL " +
            "    OR (:status = 'negative' AND COALESCE(i.availableQuantity, 0) < 0) " +
            "    OR (:status = 'out-of-stock' AND COALESCE(i.availableQuantity, 0) = 0) " +
            "    OR (:status = 'low-stock' AND COALESCE(i.availableQuantity, 0) > 0 AND COALESCE(i.availableQuantity, 0) <= i.lowStockThreshold) " +
            "    OR (:status = 'in-stock' AND COALESCE(i.availableQuantity, 0) > i.lowStockThreshold)" +
            ")",
            countQuery = "SELECT COUNT(DISTINCT i) FROM InventoryItem i " +
                    "LEFT JOIN i.variant v " +
                    "LEFT JOIN v.product p " +
                    "WHERE (:channelId IS NULL OR EXISTS (" +
                    "    SELECT 1 FROM ChannelProductVariant cpv " +
                    "    WHERE cpv.variant = v " +
                    "    AND cpv.channelProduct.channel.id = :channelId " +
                    "    AND cpv.channelProduct.channel.deletedAt IS NULL " +
                    "    AND cpv.channelProduct.mappingState = 'ACTIVE'" +
                    ")) " +
                    "AND (:localOnly = false OR NOT EXISTS (" +
                    "    SELECT 1 FROM ChannelProductVariant cpvLocal " +
                    "    WHERE cpvLocal.variant = v " +
                    "    AND cpvLocal.channelProduct.channel.deletedAt IS NULL " +
                    "    AND cpvLocal.channelProduct.mappingState = 'ACTIVE'" +
                    ")) " +
                    "AND (:warehouseId IS NULL OR i.warehouse.id = :warehouseId) " +
                    "AND (:keyword IS NULL OR LOWER(v.sku) LIKE :keyword OR LOWER(v.name) LIKE :keyword OR LOWER(p.name) LIKE :keyword) " +
                    "AND (:status IS NULL " +
                    "    OR (:status = 'negative' AND COALESCE(i.availableQuantity, 0) < 0) " +
                    "    OR (:status = 'out-of-stock' AND COALESCE(i.availableQuantity, 0) = 0) " +
                    "    OR (:status = 'low-stock' AND COALESCE(i.availableQuantity, 0) > 0 AND COALESCE(i.availableQuantity, 0) <= i.lowStockThreshold) " +
                    "    OR (:status = 'in-stock' AND COALESCE(i.availableQuantity, 0) > i.lowStockThreshold)" +
                    ")")
    Page<InventoryItem> findAllWithVariantRelationshipsFiltered(@Param("channelId") UUID channelId,
                                                                 @Param("localOnly") boolean localOnly,
                                                                 @Param("keyword") String keyword,
                                                                 @Param("status") String status,
                                                                 @Param("warehouseId") UUID warehouseId,
                                                                 Pageable pageable);

    @Query("SELECT i FROM InventoryItem i " +
            "JOIN i.variant v " +
            "JOIN v.product p " +
            "WHERE p.category.id IN :categoryIds")
    Page<InventoryItem> findByCategoryIdIn(@Param("categoryIds") List<UUID> categoryIds, Pageable pageable);

    @Query(value = "SELECT DISTINCT i FROM InventoryItem i " +
            "JOIN FETCH i.variant v " +
            "LEFT JOIN FETCH i.warehouse " +
            "JOIN FETCH v.product p " +
            "WHERE p.category.id IN :categoryIds " +
            "AND (:channelId IS NULL OR EXISTS (" +
            "    SELECT 1 FROM ChannelProductVariant cpv " +
            "    WHERE cpv.variant = v " +
            "    AND cpv.channelProduct.channel.id = :channelId " +
            "    AND cpv.channelProduct.channel.deletedAt IS NULL " +
            "    AND cpv.channelProduct.mappingState = 'ACTIVE'" +
            ")) " +
            "AND (:localOnly = false OR NOT EXISTS (" +
            "    SELECT 1 FROM ChannelProductVariant cpvLocal " +
            "    WHERE cpvLocal.variant = v " +
            "    AND cpvLocal.channelProduct.channel.deletedAt IS NULL " +
            "    AND cpvLocal.channelProduct.mappingState = 'ACTIVE'" +
            ")) " +
            "AND (:warehouseId IS NULL OR i.warehouse.id = :warehouseId) " +
            "AND (:keyword IS NULL OR LOWER(v.sku) LIKE :keyword OR LOWER(v.name) LIKE :keyword OR LOWER(p.name) LIKE :keyword) " +
            "AND (:status IS NULL " +
            "    OR (:status = 'negative' AND COALESCE(i.availableQuantity, 0) < 0) " +
            "    OR (:status = 'out-of-stock' AND COALESCE(i.availableQuantity, 0) = 0) " +
            "    OR (:status = 'low-stock' AND COALESCE(i.availableQuantity, 0) > 0 AND COALESCE(i.availableQuantity, 0) <= i.lowStockThreshold) " +
            "    OR (:status = 'in-stock' AND COALESCE(i.availableQuantity, 0) > i.lowStockThreshold)" +
            ")",
            countQuery = "SELECT COUNT(DISTINCT i) FROM InventoryItem i " +
                    "JOIN i.variant v " +
                    "JOIN v.product p " +
                    "WHERE p.category.id IN :categoryIds " +
                    "AND (:channelId IS NULL OR EXISTS (" +
                    "    SELECT 1 FROM ChannelProductVariant cpv " +
                    "    WHERE cpv.variant = v " +
                    "    AND cpv.channelProduct.channel.id = :channelId " +
                    "    AND cpv.channelProduct.channel.deletedAt IS NULL " +
                    "    AND cpv.channelProduct.mappingState = 'ACTIVE'" +
                    ")) " +
                    "AND (:localOnly = false OR NOT EXISTS (" +
                    "    SELECT 1 FROM ChannelProductVariant cpvLocal " +
                    "    WHERE cpvLocal.variant = v " +
                    "    AND cpvLocal.channelProduct.channel.deletedAt IS NULL " +
                    "    AND cpvLocal.channelProduct.mappingState = 'ACTIVE'" +
                    ")) " +
                    "AND (:warehouseId IS NULL OR i.warehouse.id = :warehouseId) " +
                    "AND (:keyword IS NULL OR LOWER(v.sku) LIKE :keyword OR LOWER(v.name) LIKE :keyword OR LOWER(p.name) LIKE :keyword) " +
                    "AND (:status IS NULL " +
                    "    OR (:status = 'negative' AND COALESCE(i.availableQuantity, 0) < 0) " +
                    "    OR (:status = 'out-of-stock' AND COALESCE(i.availableQuantity, 0) = 0) " +
                    "    OR (:status = 'low-stock' AND COALESCE(i.availableQuantity, 0) > 0 AND COALESCE(i.availableQuantity, 0) <= i.lowStockThreshold) " +
                    "    OR (:status = 'in-stock' AND COALESCE(i.availableQuantity, 0) > i.lowStockThreshold)" +
                    ")")
    Page<InventoryItem> findByCategoryIdInFiltered(@Param("categoryIds") List<UUID> categoryIds,
                                                   @Param("channelId") UUID channelId,
                                                   @Param("localOnly") boolean localOnly,
                                                   @Param("keyword") String keyword,
                                                   @Param("status") String status,
                                                   @Param("warehouseId") UUID warehouseId,
                                                   Pageable pageable);

    @Query("SELECT i FROM InventoryItem i " +
            "JOIN FETCH i.warehouse w " +
            "JOIN FETCH i.variant v " +
            "JOIN FETCH v.product p " +
            "LEFT JOIN FETCH p.category c " +
            "WHERE i.id = :id")
    Optional<InventoryItem> findDetailById(@Param("id") UUID id);

    @Query("SELECT DISTINCT i FROM InventoryItem i " +
            "LEFT JOIN FETCH i.warehouse " +
            "LEFT JOIN FETCH i.variant v " +
            "LEFT JOIN FETCH v.product p " +
            "LEFT JOIN FETCH p.category c " +
            "WHERE v.id IN :variantIds")
    List<InventoryItem> findByVariantIdInFetchAll(@Param("variantIds") Collection<UUID> variantIds);

    String INVENTORY_GROUP_UNIVERSE_SQL = """
            WITH RECURSIVE mapped AS (
                SELECT DISTINCT ON (pv.id)
                    pv.id             AS variant_id,
                    pv.product_id     AS product_id,
                    p.sku             AS product_sku,
                    pv.sku            AS variant_sku,
                    pv.name           AS variant_name,
                    p.name            AS product_name,
                    p.category_id     AS category_id,
                    cpv.external_sku  AS external_sku,
                    LOWER(BTRIM(COALESCE(cpv.external_sku, ''))) AS ext_key
                FROM product_variants pv
                JOIN products p                    ON p.id = pv.product_id AND p.deleted_at IS NULL
                LEFT JOIN channel_product_variants cpv ON cpv.variant_id = pv.id
                LEFT JOIN channel_products cp      ON cp.id = cpv.channel_product_id AND cp.mapping_state = 'ACTIVE'
                LEFT JOIN channels ch              ON ch.id = cp.channel_id AND ch.deleted_at IS NULL
                WHERE pv.deleted_at IS NULL
                ORDER BY pv.id, cpv.updated_at DESC NULLS LAST
            ),
            pkeys AS (
                SELECT product_id, LOWER(BTRIM(product_sku)) AS sk
                FROM mapped
                WHERE BTRIM(product_sku) <> ''
                UNION
                SELECT product_id, LOWER(BTRIM(variant_sku)) AS sk
                FROM mapped
                WHERE BTRIM(variant_sku) <> ''
                UNION
                SELECT product_id, LOWER(BTRIM(external_sku)) AS sk
                FROM mapped
                WHERE external_sku IS NOT NULL AND BTRIM(external_sku) <> ''
            ),
            shared AS (
                SELECT sk, array_agg(DISTINCT product_id ORDER BY product_id) AS ids
                FROM pkeys
                WHERE sk <> ''
                GROUP BY sk
                HAVING COUNT(DISTINCT product_id) > 1
            ),
            edges AS (
                SELECT DISTINCT e.a, e.b
                FROM (SELECT ids[1] AS a, u AS b FROM shared, unnest(ids[2:]) u) e
                UNION
                SELECT DISTINCT e.b, e.a
                FROM (SELECT ids[1] AS a, u AS b FROM shared, unnest(ids[2:]) u) e
            ),
            comp AS (
                SELECT DISTINCT product_id, product_id AS root FROM mapped
                UNION
                SELECT e.b, r.root FROM comp r JOIN edges e ON r.product_id = e.a
            ),
            comps AS (
                SELECT product_id, MIN(root::text) AS comp_id FROM comp GROUP BY product_id
            ),
            comp_meta AS (
                SELECT c.comp_id,
                       MIN(m.ext_key) FILTER (WHERE m.ext_key <> '') AS comp_sku
                FROM comps c
                JOIN mapped m ON m.product_id = c.product_id
                GROUP BY c.comp_id
            ),
            vg AS (
                SELECT m.variant_id,
                       m.product_id,
                       m.variant_sku,
                       m.variant_name,
                       m.product_name,
                       m.category_id,
                       m.external_sku,
                       CASE
                           WHEN cm.comp_sku IS NOT NULL AND cm.comp_sku <> ''
                           THEN 'sku:' || cm.comp_sku
                           ELSE 'product:' || c.comp_id
                       END AS group_key,
                       LOWER(BTRIM(COALESCE(m.external_sku, m.variant_sku))) AS sku_key
                FROM mapped m
                JOIN comps c ON c.product_id = m.product_id
                JOIN comp_meta cm ON cm.comp_id = c.comp_id
            ),
            group_platforms AS (
                SELECT DISTINCT
                    vg.group_key AS group_key,
                    ch.platform  AS platform
                FROM vg
                JOIN channel_product_variants cpv ON cpv.variant_id = vg.variant_id
                JOIN channel_products cp           ON cp.id = cpv.channel_product_id AND cp.mapping_state = 'ACTIVE'
                JOIN channels ch                   ON ch.id = cp.channel_id AND ch.deleted_at IS NULL
            )
            SELECT
                vg.group_key                                            AS group_key,
                MIN(vg.product_name)                                    AS product_name,
                MIN(vg.variant_sku)                                     AS variant_sku,
                (array_agg(DISTINCT vg.category_id))[1]                 AS category_id,
                array_agg(DISTINCT CAST(vg.variant_id AS text))        AS variant_ids,
                COUNT(DISTINCT vg.sku_key)                              AS sku_count,
                COALESCE(SUM(i.quantity_on_hand), 0)                    AS sort_quantity_on_hand,
                MAX(i.updated_at)                                       AS sort_updated_at
            FROM vg
            LEFT JOIN inventory_items i ON i.variant_id = vg.variant_id
            WHERE (:channelId IS NULL OR EXISTS (
                    SELECT 1 FROM channel_product_variants cpvf
                    JOIN channel_products cpf ON cpf.id = cpvf.channel_product_id AND cpf.mapping_state = 'ACTIVE'
                    JOIN channels chf        ON chf.id = cpf.channel_id AND chf.deleted_at IS NULL
                    WHERE cpvf.variant_id = vg.variant_id AND chf.id = :channelId))
              AND (:localOnly = false OR NOT EXISTS (
                    SELECT 1 FROM channel_product_variants cpvl
                    JOIN channel_products cpl ON cpl.id = cpvl.channel_product_id AND cpl.mapping_state = 'ACTIVE'
                    JOIN channels chl         ON chl.id = cpl.channel_id AND chl.deleted_at IS NULL
                    WHERE cpvl.variant_id = vg.variant_id))
              AND (:warehouseId IS NULL OR EXISTS (
                    SELECT 1 FROM inventory_items iw
                    WHERE iw.variant_id = vg.variant_id AND iw.warehouse_id = :warehouseId))
              AND (:keyword IS NULL OR LOWER(vg.variant_sku) LIKE :keyword
                   OR LOWER(vg.variant_name) LIKE :keyword
                   OR LOWER(vg.product_name) LIKE :keyword
                   OR LOWER(COALESCE(vg.external_sku, '')) LIKE :keyword)
              AND (:status IS NULL OR EXISTS (
                    SELECT 1 FROM inventory_items isf
                    WHERE isf.variant_id = vg.variant_id
                      AND (:status = 'negative' AND COALESCE(isf.available_quantity, 0) < 0
                           OR :status = 'out-of-stock' AND COALESCE(isf.available_quantity, 0) = 0
                           OR :status = 'low-stock' AND COALESCE(isf.available_quantity, 0) > 0 AND COALESCE(isf.available_quantity, 0) <= isf.low_stock_threshold
                           OR :status = 'in-stock' AND COALESCE(isf.available_quantity, 0) > isf.low_stock_threshold)))
              AND (:categoryFiltered = false OR vg.category_id IN (:categoryIds))
               AND (:platformFiltered = false OR (
                    SELECT COUNT(DISTINCT gp.platform::text)
                    FROM group_platforms gp
                    WHERE gp.group_key = vg.group_key
                      AND gp.platform::text IN (:platforms)
                  ) = :platformCount)
            GROUP BY vg.group_key
            ORDER BY vg.group_key
            """;

    @Query(value = InventoryItemRepository.INVENTORY_GROUP_UNIVERSE_SQL, nativeQuery = true)
    List<Object[]> findInventoryGroupUniverse(@Param("channelId") UUID channelId,
                                              @Param("localOnly") boolean localOnly,
                                              @Param("keyword") String keyword,
                                              @Param("status") String status,
                                              @Param("warehouseId") UUID warehouseId,
                                              @Param("categoryFiltered") boolean categoryFiltered,
                                              @Param("categoryIds") List<UUID> categoryIds,
                                              @Param("platformFiltered") boolean platformFiltered,
                                              @Param("platforms") List<String> platforms,
                                              @Param("platformCount") int platformCount);

    String INVENTORY_SUMMARY_SQL = """
            WITH RECURSIVE mapped AS (
                SELECT DISTINCT ON (pv.id)
                    pv.id             AS variant_id,
                    pv.product_id     AS product_id,
                    p.sku             AS product_sku,
                    pv.sku            AS variant_sku,
                    pv.name           AS variant_name,
                    p.name            AS product_name,
                    p.category_id     AS category_id,
                    cpv.external_sku  AS external_sku,
                    LOWER(BTRIM(COALESCE(cpv.external_sku, ''))) AS ext_key
                FROM product_variants pv
                JOIN products p ON p.id = pv.product_id AND p.deleted_at IS NULL
                LEFT JOIN channel_product_variants cpv ON cpv.variant_id = pv.id
                LEFT JOIN channel_products cp ON cp.id = cpv.channel_product_id AND cp.mapping_state = 'ACTIVE'
                LEFT JOIN channels ch ON ch.id = cp.channel_id AND ch.deleted_at IS NULL
                WHERE pv.deleted_at IS NULL
                ORDER BY pv.id, cpv.updated_at DESC NULLS LAST
            ),
            pkeys AS (
                SELECT product_id, LOWER(BTRIM(product_sku)) AS sk
                FROM mapped
                WHERE BTRIM(product_sku) <> ''
                UNION
                SELECT product_id, LOWER(BTRIM(variant_sku)) AS sk
                FROM mapped
                WHERE BTRIM(variant_sku) <> ''
                UNION
                SELECT product_id, LOWER(BTRIM(external_sku)) AS sk
                FROM mapped
                WHERE external_sku IS NOT NULL AND BTRIM(external_sku) <> ''
            ),
            shared AS (
                SELECT sk, array_agg(DISTINCT product_id ORDER BY product_id) AS ids
                FROM pkeys
                WHERE sk <> ''
                GROUP BY sk
                HAVING COUNT(DISTINCT product_id) > 1
            ),
            edges AS (
                SELECT DISTINCT e.a, e.b
                FROM (SELECT ids[1] AS a, u AS b FROM shared, unnest(ids[2:]) u) e
                UNION
                SELECT DISTINCT e.b, e.a
                FROM (SELECT ids[1] AS a, u AS b FROM shared, unnest(ids[2:]) u) e
            ),
            comp AS (
                SELECT DISTINCT product_id, product_id AS root FROM mapped
                UNION
                SELECT e.b, r.root FROM comp r JOIN edges e ON r.product_id = e.a
            ),
            comps AS (
                SELECT product_id, MIN(root::text) AS comp_id FROM comp GROUP BY product_id
            ),
            comp_meta AS (
                SELECT c.comp_id,
                       MIN(m.ext_key) FILTER (WHERE m.ext_key <> '') AS comp_sku
                FROM comps c
                JOIN mapped m ON m.product_id = c.product_id
                GROUP BY c.comp_id
            ),
            vg AS (
                SELECT m.variant_id,
                       m.product_id,
                       m.variant_sku,
                       m.variant_name,
                       m.product_name,
                       m.category_id,
                       m.external_sku,
                       CASE
                           WHEN cm.comp_sku IS NOT NULL AND cm.comp_sku <> ''
                           THEN 'sku:' || cm.comp_sku
                           ELSE 'product:' || c.comp_id
                       END AS group_key,
                       LOWER(BTRIM(COALESCE(m.external_sku, m.variant_sku))) AS sku_key
                FROM mapped m
                JOIN comps c ON c.product_id = m.product_id
                JOIN comp_meta cm ON cm.comp_id = c.comp_id
            ),
            variant_stock AS (
                SELECT
                    vg.variant_id,
                    COALESCE(SUM(i.quantity_on_hand), 0)    AS qty_on_hand,
                    COALESCE(SUM(i.available_quantity), 0)  AS available,
                    COALESCE(MAX(i.low_stock_threshold), MAX(p.low_stock_threshold), 0) AS low_stock_threshold
                FROM vg
                JOIN products p ON p.id = vg.product_id
                LEFT JOIN inventory_items i ON i.variant_id = vg.variant_id
                GROUP BY vg.variant_id
            )
            SELECT
                COUNT(DISTINCT vg.group_key) AS total_products,
                COUNT(DISTINCT vg.sku_key)   AS total_skus,
                COALESCE(SUM(vs.qty_on_hand), 0) AS total_quantity,
                COUNT(*) FILTER (WHERE vs.available > 0 AND vs.available <= vs.low_stock_threshold) AS low_stock_skus,
                COUNT(*) FILTER (WHERE vs.available = 0) AS out_of_stock_skus,
                COUNT(*) FILTER (WHERE vs.available < 0) AS negative_stock_skus
            FROM vg
            JOIN variant_stock vs ON vs.variant_id = vg.variant_id
            """;

    @Query(value = InventoryItemRepository.INVENTORY_SUMMARY_SQL, nativeQuery = true)
    List<Object[]> findInventorySummary();

    @Query("SELECT COUNT(DISTINCT i.variant.id) FROM InventoryItem i WHERE i.warehouse.id = :warehouseId")
    Integer countProductTypesByWarehouseId(@Param("warehouseId") UUID warehouseId);

    @Query("SELECT COALESCE(SUM(i.quantityOnHand), 0) FROM InventoryItem i WHERE i.warehouse.id = :warehouseId")
    Integer sumTotalStockByWarehouseId(@Param("warehouseId") UUID warehouseId);
}
