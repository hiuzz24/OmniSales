package fu.osms.inventory.repository;

import fu.osms.inventory.entity.InventoryItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID> {

    Page<InventoryItem> findByShopId(UUID shopId, Pageable pageable);

    List<InventoryItem> findByShopIdAndWarehouseId(UUID shopId, UUID warehouseId);

    Optional<InventoryItem> findByShopIdAndWarehouseIdAndVariantId(UUID shopId, UUID warehouseId, UUID variantId);

    @Query("SELECT i FROM InventoryItem i WHERE i.shop.id = :shopId " +
           "AND i.availableQuantity <= i.lowStockThreshold")
    List<InventoryItem> findLowStockItems(@Param("shopId") UUID shopId);
}
