package fu.osms.order.repository;

import fu.osms.order.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    List<OrderItem> findByOrderId(UUID orderId);

    boolean existsByVariant_Product_Id(UUID productId);

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT oi.variant.id FROM OrderItem oi WHERE oi.variant.id IN :variantIds")
    List<UUID> findVariantIdsWithOrders(@org.springframework.data.repository.query.Param("variantIds") java.util.Collection<UUID> variantIds);
}
