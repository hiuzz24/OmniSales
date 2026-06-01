package fu.osms.channel.entity;

import fu.osms.catalog.entity.ProductVariant;
import fu.osms.common.enums.SyncStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "channel_product_variants", uniqueConstraints = {
        @UniqueConstraint(name = "uq_cpv_external", columnNames = {"channel_product_id", "external_variant_id"}),
        @UniqueConstraint(name = "uq_cpv_internal", columnNames = {"channel_product_id", "variant_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChannelProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_product_id", nullable = false)
    private ChannelProduct channelProduct;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    @Column(name = "external_variant_id", nullable = false, length = 200)
    private String externalVariantId;

    @Column(name = "external_sku", length = 200)
    private String externalSku;

    @Column(name = "external_price", precision = 12, scale = 2)
    private BigDecimal externalPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status", nullable = false)
    private SyncStatus syncStatus = SyncStatus.PENDING;

    @Column(name = "last_synced_at")
    private OffsetDateTime lastSyncedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
