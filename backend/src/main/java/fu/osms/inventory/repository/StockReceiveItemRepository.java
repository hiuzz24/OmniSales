package fu.osms.inventory.repository;

import fu.osms.inventory.entity.InventoryReceiptItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StockReceiveItemRepository extends JpaRepository<InventoryReceiptItem, UUID> {

    List<InventoryReceiptItem> findByReceiptId(UUID receiptId);

    Optional<InventoryReceiptItem> findByReceiptIdAndVariantId(UUID receiptId, UUID variantId);
}
