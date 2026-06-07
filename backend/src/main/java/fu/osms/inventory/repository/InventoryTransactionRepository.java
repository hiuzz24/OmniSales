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

    Page<InventoryTransaction> findByShopId(UUID shopId, Pageable pageable);

    Page<InventoryTransaction> findByShopIdAndVariantId(UUID shopId, UUID variantId, Pageable pageable);

    List<InventoryTransaction> findByShopIdAndType(UUID shopId, InvTxnType type);

    List<InventoryTransaction> findByShopIdAndPerformedAtBetween(UUID shopId,
                                                                   OffsetDateTime from,
                                                                   OffsetDateTime to);
}
