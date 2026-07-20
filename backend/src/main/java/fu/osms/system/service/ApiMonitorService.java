package fu.osms.system.service;

import fu.osms.system.dto.ApiEndpointMetricDTO;
import fu.osms.system.dto.ApiTrafficMetricDTO;
import fu.osms.system.dto.ApiUsageSummaryDTO;

import java.util.List;

public interface ApiMonitorService {
    void recordRequest(String endpoint, String method, boolean success, long latencyMs);
    boolean isRateLimited(String identifier, String endpoint);
    ApiUsageSummaryDTO getSummaryMetrics();
    List<ApiTrafficMetricDTO> getTrafficMetrics(String range);
    List<ApiEndpointMetricDTO> getEndpointMetrics();
}
