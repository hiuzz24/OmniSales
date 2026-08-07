package fu.osms.channel.repository;

import fu.osms.channel.entity.ChannelProductVariant;
import fu.osms.common.enums.PlatformType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;

@Repository
public interface ChannelProductVariantRepository extends JpaRepository<ChannelProductVariant, UUID> {

    @Query("SELECT cpv FROM ChannelProductVariant cpv " +
            "JOIN FETCH cpv.channelProduct cp " +
            "JOIN FETCH cp.channel ch " +
            "JOIN FETCH cpv.variant v " +
            "WHERE cpv.id IN :mappingIds")
    List<ChannelProductVariant> findAllWithChannelAndVariantByIdIn(
            @Param("mappingIds") List<UUID> mappingIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT cpv FROM ChannelProductVariant cpv " +
            "JOIN FETCH cpv.channelProduct cp " +
            "JOIN FETCH cp.channel ch " +
            "JOIN FETCH cpv.variant v " +
            "WHERE cpv.id = :mappingId")
    Optional<ChannelProductVariant> findByIdForUpdate(@Param("mappingId") UUID mappingId);

    @Query(value = """
            SELECT cpv.id
            FROM channel_product_variants cpv
            JOIN channel_products cp ON cp.id = cpv.channel_product_id
            JOIN channels ch ON ch.id = cp.channel_id
            WHERE cp.mapping_state = 'ACTIVE'
              AND ch.deleted_at IS NULL
              AND CAST(ch.platform AS text) IN ('SHOPIFY', 'LAZADA', 'TIKTOK')
              AND (
                    (
                      cpv.metadata #>> '{inventoryReconciliation,state}' IN ('PENDING', 'VERIFYING')
                      AND (cpv.metadata #>> '{inventoryReconciliation,reconcileAfter}')::timestamptz <= NOW()
                    )
                    OR
                    (
                      cpv.metadata #>> '{inventoryReconciliation,state}' = 'PROCESSING'
                      AND (cpv.metadata #>> '{inventoryReconciliation,processingStartedAt}')::timestamptz <= :processingCutoff
                    )
                  )
            ORDER BY (cpv.metadata #>> '{inventoryReconciliation,reconcileAfter}')::timestamptz NULLS FIRST
            FOR UPDATE SKIP LOCKED
            LIMIT :limit
            """, nativeQuery = true)
    List<UUID> claimDueInventoryReconciliationIds(
            @Param("processingCutoff") java.time.OffsetDateTime processingCutoff,
            @Param("limit") int limit);

    List<ChannelProductVariant> findByChannelProductId(UUID channelProductId);

    Optional<ChannelProductVariant> findByChannelProductIdAndVariantId(UUID channelProductId, UUID variantId);

    Optional<ChannelProductVariant> findByChannelProductIdAndExternalVariantId(UUID channelProductId,
                                                                                 String externalVariantId);

    @Query("SELECT cpv FROM ChannelProductVariant cpv " +
            "JOIN FETCH cpv.channelProduct cp " +
            "JOIN FETCH cp.channel ch " +
            "JOIN FETCH cpv.variant v " +
            "WHERE ch.id = :channelId " +
            "AND ch.deletedAt IS NULL " +
            "AND cp.mappingState = 'ACTIVE' " +
            "AND cpv.externalVariantId = :externalVariantId")
    Optional<ChannelProductVariant> findActiveByChannelIdAndExternalVariantId(
            @Param("channelId") UUID channelId,
            @Param("externalVariantId") String externalVariantId);

    @Query("SELECT cpv FROM ChannelProductVariant cpv " +
            "JOIN FETCH cpv.channelProduct cp " +
            "JOIN FETCH cp.channel ch " +
            "JOIN FETCH cpv.variant v " +
            "LEFT JOIN FETCH v.product " +
            "WHERE ch.id = :channelId " +
            "AND ch.deletedAt IS NULL " +
            "AND cp.mappingState = 'ACTIVE' " +
            "AND cpv.externalSku = :externalSku")
    Optional<ChannelProductVariant> findActiveByChannelIdAndExternalSku(
            @Param("channelId") UUID channelId,
            @Param("externalSku") String externalSku);

    @Query(value = "SELECT cpv.* FROM channel_product_variants cpv " +
            "JOIN channel_products cp ON cp.id = cpv.channel_product_id " +
            "JOIN channels ch ON ch.id = cp.channel_id " +
            "WHERE ch.id = :channelId " +
            "AND ch.deleted_at IS NULL " +
            "AND cp.mapping_state = 'ACTIVE' " +
            "AND cpv.metadata ->> 'inventory_item_id' = :inventoryItemId",
            nativeQuery = true)
    Optional<ChannelProductVariant> findActiveByChannelIdAndInventoryItemId(
            @Param("channelId") UUID channelId,
            @Param("inventoryItemId") String inventoryItemId);

    @Query("SELECT COUNT(cpv) FROM ChannelProductVariant cpv " +
            "WHERE cpv.channelProduct.channel.id = :channelId " +
            "AND cpv.channelProduct.mappingState = 'ACTIVE'")
    long countActiveByChannelId(@Param("channelId") UUID channelId);

    @Query("SELECT cpv FROM ChannelProductVariant cpv " +
            "JOIN FETCH cpv.channelProduct cp " +
            "JOIN FETCH cp.channel ch " +
            "JOIN FETCH cpv.variant v " +
            "LEFT JOIN FETCH v.product " +
            "WHERE ch.id = :channelId " +
            "AND ch.deletedAt IS NULL " +
            "AND cp.mappingState = 'ACTIVE'")
    List<ChannelProductVariant> findActiveByChannelIdWithVariant(@Param("channelId") UUID channelId);

    @Query("SELECT cpv FROM ChannelProductVariant cpv " +
            "JOIN FETCH cpv.channelProduct cp " +
            "JOIN FETCH cp.channel ch " +
            "JOIN FETCH cpv.variant v " +
            "LEFT JOIN FETCH v.product " +
            "WHERE ch.id = :channelId " +
            "AND v.id IN :variantIds " +
            "AND ch.deletedAt IS NULL " +
            "AND cp.mappingState = 'ACTIVE'")
    List<ChannelProductVariant> findActiveByChannelIdAndVariantIdInWithVariant(
            @Param("channelId") UUID channelId,
            @Param("variantIds") List<UUID> variantIds);

    @Query("SELECT cpv FROM ChannelProductVariant cpv " +
            "JOIN FETCH cpv.channelProduct cp " +
            "JOIN FETCH cp.channel ch " +
            "WHERE cpv.variant.id = :variantId " +
            "AND ch.deletedAt IS NULL " +
            "AND cp.mappingState = 'ACTIVE' " +
            "ORDER BY cpv.updatedAt DESC")
    List<ChannelProductVariant> findActiveByVariantIdWithChannel(@Param("variantId") UUID variantId);

    @Query("SELECT cpv FROM ChannelProductVariant cpv " +
            "JOIN FETCH cpv.channelProduct cp " +
            "JOIN FETCH cp.channel ch " +
            "JOIN FETCH cpv.variant v " +
            "WHERE cpv.variant.id IN :variantIds " +
            "AND ch.deletedAt IS NULL " +
            "AND cp.mappingState = 'ACTIVE' " +
            "ORDER BY cpv.updatedAt DESC")
    List<ChannelProductVariant> findActiveByVariantIdInWithChannel(@Param("variantIds") List<UUID> variantIds);

    @Query("SELECT cpv FROM ChannelProductVariant cpv " +
            "JOIN FETCH cpv.channelProduct cp " +
            "JOIN FETCH cp.channel ch " +
            "JOIN FETCH cpv.variant v " +
            "LEFT JOIN FETCH v.product " +
            "WHERE cpv.externalSku IS NOT NULL " +
            "AND TRIM(cpv.externalSku) <> '' " +
            "AND LOWER(TRIM(cpv.externalSku)) IN :skus " +
            "AND ch.deletedAt IS NULL " +
            "AND cp.mappingState = 'ACTIVE' " +
            "AND v.isActive = true " +
            "AND v.deletedAt IS NULL " +
            "ORDER BY cpv.updatedAt DESC")
    List<ChannelProductVariant> findActiveByNormalizedExternalSkuInWithVariant(
            @Param("skus") List<String> skus);

    @Query("SELECT cpv FROM ChannelProductVariant cpv " +
            "JOIN FETCH cpv.channelProduct cp " +
            "JOIN FETCH cp.channel ch " +
            "JOIN FETCH cpv.variant v " +
            "LEFT JOIN FETCH v.product " +
            "WHERE ch.platform = :platform " +
            "AND ch.deletedAt IS NULL " +
            "AND cp.mappingState = 'ACTIVE' " +
            "ORDER BY v.sku ASC")
    List<ChannelProductVariant> findActiveByPlatformWithVariant(@Param("platform") PlatformType platform);

    @Query("SELECT cpv FROM ChannelProductVariant cpv " +
            "JOIN FETCH cpv.channelProduct cp " +
            "JOIN FETCH cp.channel ch " +
            "JOIN FETCH cpv.variant v " +
            "JOIN FETCH v.product p " +
            "WHERE ch.deletedAt IS NULL " +
            "AND cp.mappingState = 'ACTIVE' " +
            "AND v.isActive = true " +
            "AND v.deletedAt IS NULL " +
            "AND p.deletedAt IS NULL " +
            "ORDER BY LOWER(COALESCE(cpv.externalSku, v.sku)), ch.platform, ch.displayName")
    List<ChannelProductVariant> findAllActiveWithVariantAndChannel();

    @Query(value = "SELECT cpv.* FROM channel_product_variants cpv " +
            "JOIN channel_products cp ON cp.id = cpv.channel_product_id " +
            "JOIN channels ch ON ch.id = cp.channel_id " +
            "WHERE cpv.variant_id = :variantId " +
            "AND CAST(ch.platform AS text) = 'LAZADA' " +
            "AND ch.deleted_at IS NULL " +
            "AND cp.mapping_state = 'ACTIVE'",
            nativeQuery = true)
    List<ChannelProductVariant> findActiveLazadaByVariantId(@Param("variantId") UUID variantId);
}
