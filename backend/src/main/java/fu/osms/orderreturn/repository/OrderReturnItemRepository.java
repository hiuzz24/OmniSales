package fu.osms.orderreturn.repository;

import fu.osms.orderreturn.entity.OrderReturnItem;
import fu.osms.reporting.repository.projection.ReturnReportItemProjection;
import fu.osms.reporting.repository.projection.ProductReturnProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;
import java.util.Collection;
import java.time.OffsetDateTime;

public interface OrderReturnItemRepository extends JpaRepository<OrderReturnItem, UUID> {

    @Query("""
            SELECT v.id AS variantId, i.snapshotSku AS sku,
                   SUM(i.approvedQuantity) AS returnedUnits
            FROM OrderReturnItem i
                 JOIN i.orderReturn r
                 LEFT JOIN i.variant v
            WHERE r.createdAt >= :from AND r.createdAt < :to
              AND r.status <> 'REJECTED' AND r.status <> 'FAILED'
            GROUP BY v.id, i.snapshotSku
            """)
    List<ProductReturnProjection> aggregateProductReturns(@Param("from") OffsetDateTime from,
                                                           @Param("to") OffsetDateTime to);

    @Query("""
            SELECT i.orderReturn.id AS returnId,
                   i.snapshotSku AS sku,
                   i.snapshotName AS name,
                   i.approvedQuantity AS quantity,
                   i.snapshotUnitPrice AS unitPrice
            FROM OrderReturnItem i
            WHERE i.orderReturn.id IN :returnIds
            ORDER BY i.orderReturn.id, i.id
            """)
    List<ReturnReportItemProjection> findReportItems(@Param("returnIds") Collection<UUID> returnIds);

    @Query("select i from OrderReturnItem i left join fetch i.orderItem left join fetch i.variant "
            + "where i.orderReturn.id = :returnId order by i.id")
    List<OrderReturnItem> findByReturnIdWithDetails(@Param("returnId") UUID returnId);

    @Query("select i from OrderReturnItem i join fetch i.orderReturn r "
            + "where r.order.id = :orderId and r.dataValidationState = 'VALID' and r.status <> 'REJECTED'")
    List<OrderReturnItem> findValidItemsByOrderId(@Param("orderId") UUID orderId);

    void deleteByOrderReturnId(UUID returnId);
}
