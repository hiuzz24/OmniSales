package fu.osms.channel.entity;

import fu.osms.channel.enums.ChannelConnectionAction;
import fu.osms.channel.enums.ChannelConnectionLogStatus;
import fu.osms.common.enums.PlatformType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "channel_connection_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChannelConnectionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private PlatformType platform;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id")
    private Channel channel;

    @Column(name = "channel_name", length = 100)
    private String channelName;

    @Column(name = "entity_type", nullable = false, length = 50)
    @Builder.Default
    private String entityType = "CHANNEL";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ChannelConnectionAction action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ChannelConnectionLogStatus status;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
