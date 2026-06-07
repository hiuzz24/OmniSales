package fu.osms.reporting.entity;

import fu.osms.channel.entity.Channel;
import fu.osms.shop.entity.Shop;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "daily_sales_summary")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailySalesSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id")
    private Channel channel;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "order_count", nullable = false)
    private Integer orderCount = 0;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal revenue = BigDecimal.ZERO;

    @Column(name = "units_sold", nullable = false)
    private Integer unitsSold = 0;

    @Column(name = "is_dirty", nullable = false)
    private Boolean isDirty = true;

    @Column(name = "last_refreshed_at")
    private OffsetDateTime lastRefreshedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
