package fu.osms.system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiEndpointMetricDTO {
    private String endpoint;
    private String method;
    private Long requestCount;
    private Long successCount;
    private Long failCount;
    private Double avgLatencyMs;
    private Integer rateLimitPerMin;
    private Integer dailyQuota;
    private Long remainingQuota;
}
