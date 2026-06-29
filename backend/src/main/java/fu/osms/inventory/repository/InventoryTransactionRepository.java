package fu.osms.inventory.repository;

import fu.osms.inventory.entity.InventoryTransaction;
import fu.osms.inventory.enums.InvTxnType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, UUID> , JpaSpecificationExecutor<InventoryTransaction> {

    Page<InventoryTransaction> findByWarehouseId(UUID warehouseId, Pageable pageable);

    Page<InventoryTransaction> findByVariantId(UUID variantId, Pageable pageable);

    @Query(value = "SELECT t FROM InventoryTransaction t " +
            "JOIN FETCH t.warehouse " +
            "JOIN FETCH t.variant v " +
            "LEFT JOIN FETCH v.product " +
            "LEFT JOIN FETCH t.performedBy",
            countQuery = "SELECT COUNT(t) FROM InventoryTransaction t")
    Page<InventoryTransaction> findAllWithDetails(Pageable pageable);

    @Query(value = "SELECT t FROM InventoryTransaction t " +
            "JOIN FETCH t.warehouse " +
            "JOIN FETCH t.variant v " +
            "LEFT JOIN FETCH v.product " +
            "LEFT JOIN FETCH t.performedBy " +
            "WHERE v.id = :variantId",
            countQuery = "SELECT COUNT(t) FROM InventoryTransaction t WHERE t.variant.id = :variantId")
    Page<InventoryTransaction> findByVariantIdWithDetails(@Param("variantId") UUID variantId, Pageable pageable);

    List<InventoryTransaction> findByType(InvTxnType type);

    @Query(value = "SELECT * FROM inventory_transactions " +
            "WHERE variant_id = :variantId " +
            "AND CAST(type AS text) = :#{#type.name()} " +
            "ORDER BY performed_at DESC LIMIT 1",
            nativeQuery = true)
    Optional<InventoryTransaction> findFirstByVariantIdAndTypeOrderByPerformedAtDesc(
            @Param("variantId") UUID variantId,
            @Param("type") InvTxnType type);

    Optional<InventoryTransaction> findFirstByVariantIdOrderByPerformedAtDesc(UUID variantId);

    List<InventoryTransaction> findByPerformedAtBetween(OffsetDateTime from, OffsetDateTime to);
    
    List<InventoryTransaction> findByReferenceTypeAndReferenceIdAndVariantId(
            String referenceType, UUID referenceId, UUID variantId);
    
    List<InventoryTransaction> findByReferenceTypeAndReferenceId(
            String referenceType, UUID referenceId);

    @Query("SELECT DISTINCT t FROM InventoryTransaction t " +
            "JOIN FETCH t.warehouse " +
            "JOIN FETCH t.variant v " +
            "LEFT JOIN FETCH v.product " +
            "WHERE t.performedAt >= :changedSince " +
            "AND t.referenceType IN ('RECEIPT', 'ISSUE')")
    List<InventoryTransaction> findStockDocumentChangesSince(@Param("changedSince") OffsetDateTime changedSince);


    Page<InventoryTransaction> findAll(Pageable pageable);
}
