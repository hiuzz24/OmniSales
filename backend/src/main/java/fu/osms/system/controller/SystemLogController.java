package fu.osms.system.controller;

import fu.osms.common.dto.ApiResponse;
import fu.osms.common.dto.PageResponse;
import fu.osms.system.dto.SystemLogResponse;
import fu.osms.system.service.SystemLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/system-logs")
@RequiredArgsConstructor
public class SystemLogController {

    private final SystemLogService systemLogService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<SystemLogResponse>>> getLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        
        log.info("Lấy danh sách system logs: level={}, keyword={}, page={}, size={}", level, keyword, page, size);
        PageResponse<SystemLogResponse> result = systemLogService.searchLogs(level, keyword, from, to, page, size);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SystemLogResponse>> updateLog(
            @PathVariable UUID id,
            @RequestBody SystemLogResponse updateDto) {
        
        log.info("Cập nhật system log id: {}", id);
        SystemLogResponse result = systemLogService.updateLog(id, updateDto);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật nhật ký thành công", result));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteLog(@PathVariable UUID id) {
        log.info("Yêu cầu xóa system log id: {}", id);
        systemLogService.deleteLog(id);
        return ResponseEntity.ok(ApiResponse.success("Xóa dòng nhật ký thành công", null));
    }
}
