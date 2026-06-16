package fu.osms.channel.entity;

import fu.osms.catalog.entity.Product;
import fu.osms.common.enums.SyncStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "channel_products", uniqueConstraints = {
        @UniqueConstraint(name = "uq_channel_product", columnNames = {"channel_id", "external_product_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChannelProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id", nullable = false)
    private Channel channel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "external_product_id", nullable = false, length = 200)
    private String externalProductId;

    @Column(name = "external_status", length = 50)
    private String externalStatus;

    @Column(name = "mapping_state", nullable = false, length = 20)
    @Builder.Default
    private String mappingState = "ACTIVE";

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "sync_status", nullable = false)
    @Builder.Default
    private SyncStatus syncStatus = SyncStatus.PENDING;

    @Column(name = "last_synced_at")
    private OffsetDateTime lastSyncedAt;

    @Column(name = "last_sync_error", columnDefinition = "TEXT")
    private String lastSyncError;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
