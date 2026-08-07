package fu.osms.orderreturn.entity;

import fu.osms.channel.entity.Channel;
import fu.osms.common.enums.PlatformType;
import fu.osms.inventory.entity.Warehouse;
import fu.osms.order.entity.Order;
import fu.osms.orderreturn.enums.OrderReturnStatus;
import fu.osms.orderreturn.enums.ReturnAction;
import fu.osms.orderreturn.enums.ReturnActionState;
import fu.osms.orderreturn.enums.ReturnDataValidationState;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "order_returns", uniqueConstraints = {
        @UniqueConstraint(name = "uq_order_return_external", columnNames = {"channel_id", "external_return_id"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderReturn {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "channel_id", nullable = false)
    private Channel channel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private PlatformType platform;

    @Column(name = "external_return_id", nullable = false, length = 200)
    private String externalReturnId;

    @Column(name = "platform_status", length = 100)
    private String platformStatus;

    @Column(name = "platform_updated_at")
    private OffsetDateTime platformUpdatedAt;

    @Column(name = "last_webhook_event_id", length = 200)
    private String lastWebhookEventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    @Builder.Default
    private OrderReturnStatus status = OrderReturnStatus.PENDING_APPROVAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_validation_state", nullable = false, length = 20)
    @Builder.Default
    private ReturnDataValidationState dataValidationState = ReturnDataValidationState.VALID;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_action", length = 20)
    private ReturnAction lastAction;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_state", nullable = false, length = 20)
    @Builder.Default
    private ReturnActionState actionState = ReturnActionState.IDLE;

    @Column(name = "action_request_id")
    private UUID actionRequestId;

    @Column(name = "action_error", columnDefinition = "TEXT")
    private String actionError;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "inspected_at")
    private OffsetDateTime inspectedAt;

    @Column(name = "refund_confirmed_at")
    private OffsetDateTime refundConfirmedAt;

    @Column(name = "inventory_posted_at")
    private OffsetDateTime inventoryPostedAt;

    @Column(name = "last_sync_error", columnDefinition = "TEXT")
    private String lastSyncError;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
