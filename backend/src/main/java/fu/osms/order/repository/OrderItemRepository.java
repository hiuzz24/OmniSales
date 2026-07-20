package fu.osms.order.repository;

import fu.osms.order.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.time.OffsetDateTime;
import fu.osms.catalog.repository.projection.ProductSalesAggregate;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    List<OrderItem> findByOrderId(UUID orderId);

    List<OrderItem> findByOrderIdIn(List<UUID> orderIds);

    @Modifying
    @Query("DELETE FROM OrderItem oi WHERE oi.order.id = :orderId")
    void deleteByOrderId(@Param("orderId") UUID orderId);

    boolean existsByVariant_Product_Id(UUID productId);

    @Query("SELECT DISTINCT oi.variant.id FROM OrderItem oi WHERE oi.variant.id IN :variantIds")
    List<UUID> findVariantIdsWithOrders(@Param("variantIds") java.util.Collection<UUID> variantIds);

    @Query(value = """
            SELECT COALESCE(SUM(oi.quantity), 0)::bigint AS "unitsSold",
                   COALESCE(SUM(oi.total_price), 0) AS "revenue",
                   COALESCE(SUM(CASE
                       WHEN COALESCE(oi.cost_price, pv.cost_price) IS NOT NULL
                       THEN COALESCE(oi.cost_price, pv.cost_price) * oi.quantity
                       ELSE 0 END), 0) AS "resolvedCost",
                   COUNT(*) FILTER (WHERE oi.cost_price IS NULL AND pv.cost_price IS NOT NULL)::bigint AS "fallbackCostCount",
                   COUNT(*) FILTER (WHERE oi.cost_price IS NULL AND pv.cost_price IS NULL)::bigint AS "missingCostCount"
            FROM order_items oi
            JOIN product_variants pv ON pv.id = oi.variant_id
            JOIN orders o ON o.id = oi.order_id
            WHERE pv.product_id = :productId
              AND CAST(o.status AS text) = 'DELIVERED'
            """, nativeQuery = true)
    ProductSalesAggregate aggregateDeliveredSalesByProduct(@Param("productId") UUID productId);

    @Query(value = """
            SELECT COALESCE(SUM(oi.quantity), 0)::bigint AS "unitsSold",
                   COALESCE(SUM(oi.total_price), 0) AS "revenue",
                   COALESCE(SUM(CASE
                       WHEN COALESCE(oi.cost_price, pv.cost_price) IS NOT NULL
                       THEN COALESCE(oi.cost_price, pv.cost_price) * oi.quantity
                       ELSE 0 END), 0) AS "resolvedCost",
                   COUNT(*) FILTER (WHERE oi.cost_price IS NULL AND pv.cost_price IS NOT NULL)::bigint AS "fallbackCostCount",
                   COUNT(*) FILTER (WHERE oi.cost_price IS NULL AND pv.cost_price IS NULL)::bigint AS "missingCostCount"
            FROM order_items oi
            JOIN product_variants pv ON pv.id = oi.variant_id
            JOIN orders o ON o.id = oi.order_id
            WHERE pv.product_id = :productId
              AND CAST(o.status AS text) = 'DELIVERED'
              AND COALESCE(o.status_changed_at, o.updated_at) >= :from
              AND COALESCE(o.status_changed_at, o.updated_at) < :to
            """, nativeQuery = true)
    ProductSalesAggregate aggregateDeliveredSalesByProductBetween(
            @Param("productId") UUID productId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to);
}
