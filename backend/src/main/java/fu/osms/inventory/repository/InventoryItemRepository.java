package fu.osms.inventory.repository;

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

    Optional<InventoryItem> findByWarehouseIdAndVariantId(UUID warehouseId, UUID variantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM InventoryItem i WHERE i.warehouse.id = :warehouseId AND i.variant.id = :variantId")
    Optional<InventoryItem> findByWarehouseIdAndVariantIdWithLock(@Param("warehouseId") UUID warehouseId,
                                                                   @Param("variantId") UUID variantId);

    @Query("SELECT i FROM InventoryItem i WHERE i.availableQuantity <= i.lowStockThreshold")
    List<InventoryItem> findLowStockItems();

    List<InventoryItem> findByVariantIdIn(Collection<UUID> variantIds);

    @Query(value = "SELECT i FROM InventoryItem i " +
            "LEFT JOIN FETCH i.warehouse " +
            "LEFT JOIN FETCH i.variant",
            countQuery = "SELECT COUNT(i) FROM InventoryItem i")
    Page<InventoryItem> findAllWithVariantRelationships(Pageable pageable);

    @Query("SELECT i FROM InventoryItem i " +
            "JOIN i.variant v " +
            "JOIN v.product p " +
            "WHERE p.category.id IN :categoryIds")
    Page<InventoryItem> findByCategoryIdIn(@Param("categoryIds") List<UUID> categoryIds, Pageable pageable);

    @Query("SELECT i FROM InventoryItem i " +
            "JOIN FETCH i.warehouse w " +
            "JOIN FETCH i.variant v " +
            "JOIN FETCH v.product p " +
            "JOIN FETCH p.category c " +
            "WHERE i.id = :id")
    Optional<InventoryItem> findDetailById(@Param("id") UUID id);
}


