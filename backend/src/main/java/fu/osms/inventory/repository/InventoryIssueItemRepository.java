package fu.osms.inventory.repository;

import fu.osms.inventory.entity.InventoryIssueItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InventoryIssueItemRepository extends JpaRepository<InventoryIssueItem, UUID> {

    @Query("SELECT iii FROM InventoryIssueItem iii " +
            "LEFT JOIN FETCH iii.productVariant pv " +
            "LEFT JOIN FETCH pv.product " +
            "WHERE iii.inventoryIssue.id = :issueId")
    List<InventoryIssueItem> findByIssueIdWithDetails(@Param("issueId") UUID issueId);

    List<InventoryIssueItem> findByInventoryIssueId(UUID issueId);
}
