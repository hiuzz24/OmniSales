package fu.osms.system.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "api_metrics_daily", uniqueConstraints = {
    @UniqueConstraint(name = "uq_api_metric_endpoint_hour", columnNames = {"endpoint", "method", "recorded_date", "recorded_hour"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiMetricDaily {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String endpoint;

    @Column(nullable = false, length = 10)
    private String method;

    @Column(name = "request_count", nullable = false)
    @Builder.Default
    private Long requestCount = 0L;

    @Column(name = "success_count", nullable = false)
    @Builder.Default
    private Long successCount = 0L;

    @Column(name = "fail_count", nullable = false)
    @Builder.Default
    private Long failCount = 0L;

    @Column(name = "avg_latency_ms", nullable = false)
    @Builder.Default
    private Double avgLatencyMs = 0.0;

    @Column(name = "recorded_date", nullable = false)
    private LocalDate recordedDate;

    @Column(name = "recorded_hour", nullable = false)
    private Integer recordedHour;
}
