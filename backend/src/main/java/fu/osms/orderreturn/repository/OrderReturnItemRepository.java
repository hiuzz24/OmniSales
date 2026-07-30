package fu.osms.orderreturn.repository;

import fu.osms.orderreturn.entity.OrderReturnItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OrderReturnItemRepository extends JpaRepository<OrderReturnItem, UUID> {

    @Query("select i from OrderReturnItem i left join fetch i.orderItem left join fetch i.variant "
            + "where i.orderReturn.id = :returnId order by i.id")
    List<OrderReturnItem> findByReturnIdWithDetails(@Param("returnId") UUID returnId);

    @Query("select i from OrderReturnItem i join fetch i.orderReturn r "
            + "where r.order.id = :orderId and r.dataValidationState = 'VALID' and r.status <> 'REJECTED'")
    List<OrderReturnItem> findValidItemsByOrderId(@Param("orderId") UUID orderId);

    void deleteByOrderReturnId(UUID returnId);
}
