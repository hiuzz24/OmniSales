package fu.osms.reporting.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "report_results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_config_id", nullable = false)
    private ReportConfig reportConfig;

    @Column(name = "generated_at", nullable = false)
    private OffsetDateTime generatedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameters_used", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> parametersUsed;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_data", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> resultData;

    @Column(name = "row_count", nullable = false)
    private Integer rowCount;

    @Column(name = "execution_time_ms")
    private Integer executionTimeMs;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (generatedAt == null) {
            generatedAt = OffsetDateTime.now();
        }
    }
}
