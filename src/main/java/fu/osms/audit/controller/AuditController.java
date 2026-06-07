package fu.osms.audit.controller;

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
    public ResponseEntity<ApiResponse<PageResponse<AuditLog>>> getByShop(
            @RequestParam UUID shopId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(auditService.getByShopId(shopId, page, size)));
    }

    @GetMapping("/entity")
    public ResponseEntity<ApiResponse<PageResponse<AuditLog>>> getByEntity(
            @RequestParam UUID shopId,
            @RequestParam String entityType,
            @RequestParam UUID entityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                auditService.getByEntity(shopId, entityType, entityId, page, size)));
    }

    @GetMapping("/actor/{actorId}")
    public ResponseEntity<ApiResponse<PageResponse<AuditLog>>> getByActor(
            @RequestParam UUID shopId,
            @PathVariable UUID actorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(auditService.getByActor(shopId, actorId, page, size)));
    }

    @GetMapping("/date-range")
    public ResponseEntity<ApiResponse<PageResponse<AuditLog>>> getByDateRange(
            @RequestParam UUID shopId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(auditService.getByDateRange(shopId, from, to, page, size)));
    }
}
