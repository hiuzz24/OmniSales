package fu.osms.orderreturn.entity;

import fu.osms.catalog.entity.ProductVariant;
import fu.osms.order.entity.OrderItem;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "order_return_items", uniqueConstraints = {
        @UniqueConstraint(name = "uq_return_item_identity", columnNames = {"return_id", "external_identity_key"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderReturnItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "return_id", nullable = false)
    private OrderReturn orderReturn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id")
    private OrderItem orderItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id")
    private ProductVariant variant;

    @Column(name = "external_order_item_id", length = 200)
    private String externalOrderItemId;

    @Column(name = "external_return_item_id", length = 200)
    private String externalReturnItemId;

    @Column(name = "external_identity_key", nullable = false, length = 420)
    private String externalIdentityKey;

    @Column(name = "requested_quantity", nullable = false)
    private Integer requestedQuantity;

    @Column(name = "approved_quantity", nullable = false)
    private Integer approvedQuantity;

    @Column(name = "received_quantity")
    private Integer receivedQuantity;

    @Column(name = "restockable_quantity")
    private Integer restockableQuantity;

    @Column(name = "damaged_quantity")
    private Integer damagedQuantity;

    @Column(name = "missing_quantity")
    private Integer missingQuantity;

    @Column(name = "refunded_quantity")
    private Integer refundedQuantity;

    @Column(name = "snapshot_sku", length = 100)
    private String snapshotSku;

    @Column(name = "snapshot_name", nullable = false, length = 500)
    private String snapshotName;

    @Column(name = "snapshot_unit_price", precision = 12, scale = 2)
    private BigDecimal snapshotUnitPrice;

    @Column(name = "snapshot_cost_price", precision = 12, scale = 2)
    private BigDecimal snapshotCostPrice;
}
