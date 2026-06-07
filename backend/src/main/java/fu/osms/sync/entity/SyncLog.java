package fu.osms.sync.entity;

import fu.osms.auth.entity.User;
import fu.osms.channel.entity.Channel;
import fu.osms.common.enums.SyncStatus;
import fu.osms.shop.entity.Shop;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "sync_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id")
    private Channel channel;

    @Column(name = "job_type", nullable = false, length = 50)
    private String jobType;

    @Column(name = "idempotency_key", unique = true, length = 200)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SyncStatus status = SyncStatus.PENDING;

    @Column(name = "total_items")
    private Integer totalItems;

    @Column(name = "success_count", nullable = false)
    private Integer successCount = 0;

    @Column(name = "fail_count", nullable = false)
    private Integer failCount = 0;

    @Column(name = "error_summary", columnDefinition = "TEXT")
    private String errorSummary;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "retry_of_id")
    private SyncLog retryOf;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "triggered_by")
    private User triggeredBy;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        if (startedAt == null) {
            startedAt = OffsetDateTime.now();
        }
    }
}
