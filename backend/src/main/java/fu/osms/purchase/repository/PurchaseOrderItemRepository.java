package fu.osms.purchase.repository;

import fu.osms.purchase.entity.PurchaseOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PurchaseOrderItemRepository extends JpaRepository<PurchaseOrderItem, UUID> {
    @Query("SELECT i.variant.id, COALESCE(SUM(i.quantity), 0) FROM PurchaseOrderItem i " +
            "WHERE i.variant.id IN :variantIds AND i.purchaseOrder.status IN " +
            "(fu.osms.purchase.enums.PurchaseOrderStatus.SENT_TO_SUPPLIER, fu.osms.purchase.enums.PurchaseOrderStatus.RECEIVING) " +
            "GROUP BY i.variant.id")
    List<Object[]> sumIncomingByVariantIds(@Param("variantIds") Collection<UUID> variantIds);
}
