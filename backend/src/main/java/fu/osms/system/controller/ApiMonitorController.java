package fu.osms.system.controller;

import fu.osms.common.dto.ApiResponse;
import fu.osms.system.dto.ApiEndpointMetricDTO;
import fu.osms.system.dto.ApiTrafficMetricDTO;
import fu.osms.system.dto.ApiUsageSummaryDTO;
import fu.osms.system.service.ApiMonitorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/admin/monitor")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class ApiMonitorController {

    private final ApiMonitorService apiMonitorService;

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<ApiUsageSummaryDTO>> getSummaryMetrics() {
        log.info("Request API usage summary metrics for Admin");
        ApiUsageSummaryDTO summary = apiMonitorService.getSummaryMetrics();
        return ResponseEntity.ok(ApiResponse.success(summary));
    }

    @GetMapping("/traffic")
    public ResponseEntity<ApiResponse<List<ApiTrafficMetricDTO>>> getTrafficMetrics(
            @RequestParam(defaultValue = "today") String range) {
        log.info("Request API traffic history with range: {}", range);
        List<ApiTrafficMetricDTO> traffic = apiMonitorService.getTrafficMetrics(range);
        return ResponseEntity.ok(ApiResponse.success(traffic));
    }

    @GetMapping("/endpoints")
    public ResponseEntity<ApiResponse<List<ApiEndpointMetricDTO>>> getEndpointMetrics() {
        log.info("Request detailed endpoint metrics for Admin");
        List<ApiEndpointMetricDTO> endpoints = apiMonitorService.getEndpointMetrics();
        return ResponseEntity.ok(ApiResponse.success(endpoints));
    }
}
