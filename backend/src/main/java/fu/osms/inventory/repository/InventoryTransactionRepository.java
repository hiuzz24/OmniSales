package fu.osms.inventory.repository;

import fu.osms.inventory.entity.InventoryTransaction;
import fu.osms.inventory.enums.InvTxnType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, UUID> {

    Page<InventoryTransaction> findByWarehouseId(UUID warehouseId, Pageable pageable);

    Page<InventoryTransaction> findByVariantId(UUID variantId, Pageable pageable);

    List<InventoryTransaction> findByType(InvTxnType type);

    List<InventoryTransaction> findByPerformedAtBetween(OffsetDateTime from, OffsetDateTime to);
    
    List<InventoryTransaction> findByReferenceTypeAndReferenceIdAndVariantId(
            String referenceType, UUID referenceId, UUID variantId);
    
    List<InventoryTransaction> findByReferenceTypeAndReferenceId(
            String referenceType, UUID referenceId);
}
