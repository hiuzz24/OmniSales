package fu.osms.inventory.repository;


import fu.osms.inventory.entity.StockTransferItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StokeTransferItemRepository extends JpaRepository<StockTransferItem, UUID> {
}
