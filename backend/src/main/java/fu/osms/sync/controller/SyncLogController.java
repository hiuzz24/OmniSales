package fu.osms.sync.controller;

import fu.osms.common.dto.ApiResponse;
import fu.osms.common.dto.PageResponse;
import fu.osms.common.enums.SyncStatus;
import fu.osms.sync.dto.SyncLogResponse;
import fu.osms.sync.service.SyncLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/sync-logs")
@RequiredArgsConstructor
public class SyncLogController {

    private final SyncLogService syncLogService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<SyncLogResponse>>> getSyncLogs(
            @RequestParam(required = false) SyncStatus status,
            @RequestParam(required = false) UUID channelId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        PageResponse<SyncLogResponse> response = syncLogService.search(status, channelId, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
