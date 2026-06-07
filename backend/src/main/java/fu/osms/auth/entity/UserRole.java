package fu.osms.auth.entity;

import fu.osms.shop.entity.Shop;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_roles", uniqueConstraints = {
        @UniqueConstraint(name = "uq_user", columnNames = {"user_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserRole {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id")
    private Shop shop;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "granted_by")
    private User grantedBy;

    @Column(name = "granted_at", nullable = false)
    private OffsetDateTime grantedAt;

    @PrePersist
    protected void onCreate() {
        if (grantedAt == null) {
            grantedAt = OffsetDateTime.now();
        }
    }
}
