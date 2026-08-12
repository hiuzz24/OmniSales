package fu.osms.order.repository;

import fu.osms.order.entity.OrderItem;
import fu.osms.reporting.repository.projection.ProductSalesProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.OffsetDateTime;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    @Query("""
            SELECT p.id AS productId, v.id AS variantId, oi.sku AS sku,
                   COALESCE(p.name, oi.name) AS productName, v.name AS variantName,
                   SUM(oi.quantity) AS unitsSold,
                   COALESCE(SUM(CASE WHEN oi.totalPrice IS NOT NULL THEN oi.totalPrice
                       ELSE (oi.unitPrice * oi.quantity) - oi.discountAmount END), 0) AS revenue
            FROM OrderItem oi
                 JOIN oi.order o
                 LEFT JOIN oi.variant v
                 LEFT JOIN v.product p
            WHERE o.createdAt >= :from AND o.createdAt < :to AND o.status <> 'CANCELLED'
            GROUP BY p.id, v.id, oi.sku, p.name, oi.name, v.name
            """)
    List<ProductSalesProjection> aggregateProductSales(@Param("from") OffsetDateTime from,
                                                        @Param("to") OffsetDateTime to);

    List<OrderItem> findByOrderId(UUID orderId);

    List<OrderItem> findByOrderIdIn(List<UUID> orderIds);

    Optional<OrderItem> findByOrderIdAndExternalItemId(UUID orderId, String externalItemId);

    @Modifying
    @Query("DELETE FROM OrderItem oi WHERE oi.order.id = :orderId")
    void deleteByOrderId(@Param("orderId") UUID orderId);

    boolean existsByVariant_Product_Id(UUID productId);

    @Query("SELECT DISTINCT oi.variant.id FROM OrderItem oi WHERE oi.variant.id IN :variantIds")
    List<UUID> findVariantIdsWithOrders(@Param("variantIds") java.util.Collection<UUID> variantIds);

}
