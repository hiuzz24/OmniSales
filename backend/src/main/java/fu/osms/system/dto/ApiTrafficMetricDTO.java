package fu.osms.system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiTrafficMetricDTO {
    private String timeLabel; // e.g., "10:00" or "2026-07-05"
    private Long requestCount;
    private Long successCount;
    private Long failCount;
}
