package fu.osms.audit.controller;

import fu.osms.audit.dto.response.AuditLogResponse;
import fu.osms.audit.entity.AuditLog;
import fu.osms.audit.service.AuditService;
import fu.osms.common.dto.ApiResponse;
import fu.osms.common.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AuditLogResponse>>> getLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        PageResponse<AuditLogResponse> result = auditService.searchOrderLogs(keyword, from, to, action, page, size);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/entity/{entityType}/{entityId}")
    public ResponseEntity<ApiResponse<PageResponse<AuditLogResponse>>> getByEntity(
            @PathVariable String entityType,
            @PathVariable UUID entityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(auditService.getByEntity(entityType, entityId, page, size)));
    }

    @GetMapping("/actor/{actorId}")
    public ResponseEntity<ApiResponse<PageResponse<AuditLogResponse>>> getByActor(
            @PathVariable UUID actorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(auditService.getByActor(actorId, page, size)));
    }

    @GetMapping("/date-range")
    public ResponseEntity<ApiResponse<PageResponse<AuditLogResponse>>> getByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(auditService.getByDateRange(from, to, page, size)));
    }
}
