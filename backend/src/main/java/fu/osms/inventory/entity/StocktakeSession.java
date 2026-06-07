package fu.osms.inventory.entity;

import fu.osms.auth.entity.User;
import fu.osms.shop.entity.Shop;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "stocktake_sessions",
        uniqueConstraints = @UniqueConstraint(name = "uq_stocktake_code", columnNames = {"shop_id", "session_code"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StocktakeSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Column(name = "session_code", nullable = false, length = 100)
    private String sessionCode;

    @Column(name = "scheduled_date")
    private LocalDate scheduledDate;

    @Column(nullable = false, length = 20)
    private String status = "DRAFT";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpsert() {
        updatedAt = OffsetDateTime.now();
    }
}
