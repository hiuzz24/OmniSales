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
            "))",
            countQuery = "SELECT COUNT(DISTINCT i) FROM InventoryItem i " +
                    "LEFT JOIN i.variant v " +
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
                    "))")
    Page<InventoryItem> findAllWithVariantRelationshipsFiltered(@Param("channelId") UUID channelId,
                                                                 @Param("localOnly") boolean localOnly,
                                                                 Pageable pageable);

    @Query("SELECT i FROM InventoryItem i " +
            "JOIN i.variant v " +
            "JOIN v.product p " +
            "WHERE p.category.id IN :categoryIds")
    Page<InventoryItem> findByCategoryIdIn(@Param("categoryIds") List<UUID> categoryIds, Pageable pageable);

    @Query(value = "SELECT DISTINCT i FROM InventoryItem i " +
            "JOIN FETCH i.variant v " +
            "LEFT JOIN FETCH i.warehouse " +
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
            "))",
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
                    "))")
    Page<InventoryItem> findByCategoryIdInFiltered(@Param("categoryIds") List<UUID> categoryIds,
                                                   @Param("channelId") UUID channelId,
                                                   @Param("localOnly") boolean localOnly,
                                                   Pageable pageable);

    @Query("SELECT i FROM InventoryItem i " +
            "JOIN FETCH i.warehouse w " +
            "JOIN FETCH i.variant v " +
            "JOIN FETCH v.product p " +
            "LEFT JOIN FETCH p.category c " +
            "WHERE i.id = :id")
    Optional<InventoryItem> findDetailById(@Param("id") UUID id);
}
