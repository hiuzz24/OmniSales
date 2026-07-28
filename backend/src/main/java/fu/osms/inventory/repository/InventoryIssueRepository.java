package fu.osms.inventory.repository;

import fu.osms.inventory.entity.InventoryIssue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;

@Repository
public interface InventoryIssueRepository extends JpaRepository<InventoryIssue, UUID>, JpaSpecificationExecutor<InventoryIssue> {

    @Query("SELECT ii FROM InventoryIssue ii " +
            "LEFT JOIN FETCH ii.warehouse " +
            "LEFT JOIN FETCH ii.createdBy " +
            "LEFT JOIN FETCH ii.items " +
            "WHERE ii.id = :id")
    Optional<InventoryIssue> findByIdWithDetails(@Param("id") UUID id);

    Optional<InventoryIssue> findByReferenceId(UUID referenceId);

    List<InventoryIssue> findByReferenceIdAndIssueTypeAndStatusIn(
            UUID referenceId, String issueType, List<String> statuses);

    Optional<InventoryIssue> findFirstByReferenceIdAndIssueTypeAndStatus(
            UUID referenceId, String issueType, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ii FROM InventoryIssue ii LEFT JOIN FETCH ii.items WHERE ii.id = :id")
    Optional<InventoryIssue> findByIdForUpdate(@Param("id") UUID id);

    @Query("SELECT COUNT(ii) FROM InventoryIssue ii WHERE ii.status = :status")
    Long countByStatus(@Param("status") String status);

    Long countByIssueType(String issueType);

    @Query("SELECT COALESCE(SUM(ii.totalCost), 0) FROM InventoryIssue ii")
    BigDecimal sumTotalCost();

    @Query(value = "SELECT COUNT(*) FROM inventory_issues WHERE EXTRACT(YEAR FROM created_at) = :year", nativeQuery = true)
    long countByCreatedYear(@Param("year") int year);

    Optional<InventoryIssue> findTopByIssueCodeStartingWithOrderByIssueCodeDesc(String prefix);

    @Query("SELECT COUNT(ii) FROM InventoryIssue ii " +
            "WHERE ii.issueType IN ('ORDER', 'ADJUSTMENT', 'DISPOSAL', 'TRANSFER')")
    Long countDeliveries();

    @Query("SELECT DISTINCT item.productVariant.id FROM InventoryIssueItem item " +
            "WHERE item.inventoryIssue.status = 'CONFIRMED' " +
            "AND ((item.inventoryIssue.createdAt > :changedSince AND item.inventoryIssue.createdAt <= :changedUntil) " +
            "OR (item.inventoryIssue.updatedAt > :changedSince AND item.inventoryIssue.updatedAt <= :changedUntil))")
    List<UUID> findChangedAppliedVariantIdsBetween(@Param("changedSince") OffsetDateTime changedSince,
                                                   @Param("changedUntil") OffsetDateTime changedUntil);

    @Query("SELECT DISTINCT item.productVariant.id FROM InventoryIssueItem item " +
            "WHERE item.inventoryIssue.status = 'CONFIRMED' " +
            "AND (item.inventoryIssue.createdAt <= :changedUntil OR item.inventoryIssue.updatedAt <= :changedUntil)")
    List<UUID> findConfirmedVariantIdsUpTo(@Param("changedUntil") OffsetDateTime changedUntil);

    @Query("SELECT DISTINCT item.inventoryIssue.warehouse.id FROM InventoryIssueItem item " +
            "WHERE item.inventoryIssue.status = 'CONFIRMED' " +
            "AND ((item.inventoryIssue.createdAt > :changedSince AND item.inventoryIssue.createdAt <= :changedUntil) " +
            "OR (item.inventoryIssue.updatedAt > :changedSince AND item.inventoryIssue.updatedAt <= :changedUntil))")
    List<UUID> findChangedAppliedWarehouseIdsBetween(@Param("changedSince") OffsetDateTime changedSince,
                                                     @Param("changedUntil") OffsetDateTime changedUntil);

    @Query("SELECT DISTINCT item.productVariant.id FROM InventoryIssueItem item " +
            "JOIN ChannelProductVariant cpv ON cpv.variant.id = item.productVariant.id " +
            "JOIN cpv.channelProduct cp " +
            "JOIN cp.channel ch " +
            "WHERE item.inventoryIssue.status = 'CONFIRMED' " +
            "AND ch.deletedAt IS NULL " +
            "AND cp.mappingState = 'ACTIVE' " +
            "AND (cpv.lastSyncedAt IS NULL OR cpv.lastSyncedAt < item.inventoryIssue.createdAt)")
    List<UUID> findConfirmedVariantIdsPendingMarketplaceSync();
}
