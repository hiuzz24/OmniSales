package fu.osms.inventory.repository;

import fu.osms.inventory.entity.InventoryIssue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryIssueRepository extends JpaRepository<InventoryIssue, UUID>, JpaSpecificationExecutor<InventoryIssue> {

    @Query("SELECT ii FROM InventoryIssue ii " +
            "LEFT JOIN FETCH ii.warehouse " +
            "LEFT JOIN FETCH ii.createdBy " +
            "LEFT JOIN FETCH ii.items " +
            "WHERE ii.id = :id")
    Optional<InventoryIssue> findByIdWithDetails(@Param("id") UUID id);

    Optional<InventoryIssue> findByReferenceId(UUID referenceId);

    @Query("SELECT COUNT(ii) FROM InventoryIssue ii WHERE ii.status = :status")
    Long countByStatus(@Param("status") String status);

    Long countByIssueType(String issueType);

    @Query("SELECT COALESCE(SUM(ii.totalCost), 0) FROM InventoryIssue ii")
    BigDecimal sumTotalCost();

    @Query(value = "SELECT COUNT(*) FROM inventory_issues WHERE EXTRACT(YEAR FROM created_at) = :year", nativeQuery = true)
    long countByCreatedYear(@Param("year") int year);

    @Query("SELECT COUNT(ii) FROM InventoryIssue ii " +
            "WHERE ii.issueType IN ('ORDER', 'ADJUSTMENT', 'DISPOSAL', 'TRANSFER')")
    Long countDeliveries();
}
