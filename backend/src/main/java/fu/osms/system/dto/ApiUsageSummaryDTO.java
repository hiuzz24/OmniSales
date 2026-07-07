package fu.osms.system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiUsageSummaryDTO {
    private Long totalRequestsToday;
    private Double successRate;
    private Double avgLatencyMs;
    private Integer activeAlertsCount;
}
