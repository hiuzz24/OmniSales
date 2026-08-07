package fu.osms.purchase.entity;

import fu.osms.catalog.entity.ProductVariant;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "purchase_order_items", uniqueConstraints =
        @UniqueConstraint(name = "uq_purchase_order_variant", columnNames = {"purchase_order_id", "variant_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "total_cost", insertable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal totalCost;

    /** Actual received quantity entered by sales/owner during inspection. Null = not yet inspected. */
    @Column(name = "actual_quantity")
    private Integer actualQuantity;

    /** Note when actual quantity differs from ordered (e.g., surplus/shortage explanation). */
    @Column(name = "surplus_note", columnDefinition = "TEXT")
    private String surplusNote;
}
