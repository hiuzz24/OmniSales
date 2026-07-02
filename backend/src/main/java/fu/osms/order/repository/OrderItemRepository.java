package fu.osms.order.repository;

import fu.osms.order.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

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
}
