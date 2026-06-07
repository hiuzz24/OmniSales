package fu.osms.inventory.entity;

import fu.osms.catalog.entity.ProductVariant;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "inventory_receipt_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryReceiptItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_id", nullable = false)
    private InventoryReceipt receipt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "total_cost", insertable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal totalCost;

    @Column(name = "avg_cost_before", precision = 12, scale = 2)
    private BigDecimal avgCostBefore;

    @Column(name = "avg_cost_after", precision = 12, scale = 2)
    private BigDecimal avgCostAfter;

    @Column(columnDefinition = "TEXT")
    private String notes;
}
