package fu.osms.sync.order.pull.entity;

import fu.osms.sync.entity.SyncLog;
import fu.osms.sync.order.pull.OrderPullJobState;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "order_pull_jobs", indexes = {
        @Index(name = "idx_order_pull_jobs_recovery", columnList = "state, updated_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uq_order_pull_jobs_sync_log", columnNames = "sync_log_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderPullJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sync_log_id", nullable = false)
    private SyncLog syncLog;

    @Column(name = "from_time", nullable = false)
    private OffsetDateTime fromTime;

    @Column(name = "to_time", nullable = false)
    private OffsetDateTime toTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private OrderPullJobState state = OrderPullJobState.PENDING;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private Integer attemptCount = 0;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "last_heartbeat_at")
    private OffsetDateTime lastHeartbeatAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}

