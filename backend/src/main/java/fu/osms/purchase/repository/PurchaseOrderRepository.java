package fu.osms.purchase.repository;

import fu.osms.purchase.entity.PurchaseOrder;
import fu.osms.purchase.enums.PurchaseOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {
    boolean existsByOrderCode(String orderCode);

    Optional<PurchaseOrder> findTopByOrderCodeStartingWithOrderByOrderCodeDesc(String prefix);

    @Query(value = "SELECT DISTINCT po FROM PurchaseOrder po " +
            "LEFT JOIN FETCH po.supplier LEFT JOIN FETCH po.warehouse LEFT JOIN FETCH po.createdBy " +
            "ORDER BY po.createdAt DESC",
            countQuery = "SELECT COUNT(po) FROM PurchaseOrder po")
    Page<PurchaseOrder> findAllWithDetails(Pageable pageable);

    @Query(value = "SELECT DISTINCT po FROM PurchaseOrder po " +
            "LEFT JOIN FETCH po.supplier LEFT JOIN FETCH po.warehouse LEFT JOIN FETCH po.createdBy " +
            "WHERE po.status = :status ORDER BY po.createdAt DESC",
            countQuery = "SELECT COUNT(po) FROM PurchaseOrder po WHERE po.status = :status")
    Page<PurchaseOrder> findAllWithDetailsByStatus(@Param("status") PurchaseOrderStatus status, Pageable pageable);

    @Query("SELECT DISTINCT po FROM PurchaseOrder po LEFT JOIN FETCH po.items i LEFT JOIN FETCH i.variant v " +
            "LEFT JOIN FETCH v.product LEFT JOIN FETCH po.supplier LEFT JOIN FETCH po.warehouse " +
            "LEFT JOIN FETCH po.createdBy LEFT JOIN FETCH po.receipts WHERE po.id = :id")
    Optional<PurchaseOrder> findByIdWithDetails(@Param("id") UUID id);

    List<PurchaseOrder> findByStatusAndSentAtLessThanEqual(PurchaseOrderStatus status, OffsetDateTime cutoff);

    long countByStatus(PurchaseOrderStatus status);
}
