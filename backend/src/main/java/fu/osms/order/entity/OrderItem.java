package fu.osms.order.entity;

import fu.osms.catalog.entity.ProductVariant;
import fu.osms.channel.entity.ChannelProductVariant;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id")
    private ProductVariant variant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_variant_id")
    private ChannelProductVariant channelVariant;

    @Column(name = "external_item_id", length = 200)
    private String externalItemId;

    @Column(length = 100)
    private String sku;

    @Column(nullable = false, length = 500)
    private String name;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "total_price", insertable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "cost_price", precision = 12, scale = 2)
    private BigDecimal costPrice;
}
