package fu.osms.system.controller;

import fu.osms.common.dto.ApiResponse;
import fu.osms.system.dto.SystemSettingRequest;
import fu.osms.system.entity.SystemSetting;
import fu.osms.system.service.SystemSettingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
// Cho phép ADMIN và OWNER (business owner có full access tới settings).
// KHÔNG dùng SYSTEM_ADMIN — role này không tồn tại trong DB (xem RenderDataSeeder.SEED_ROLES).
@PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
public class SystemSettingController {

    private final SystemSettingService systemSettingService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<SystemSetting>>> getAllSettings() {
        log.info("Request all system settings for Admin");
        List<SystemSetting> settings = systemSettingService.getAllSettings();
        return ResponseEntity.ok(ApiResponse.success(settings));
    }

    @PutMapping("/{key}")
    public ResponseEntity<ApiResponse<Void>> updateSetting(
            @PathVariable String key,
            @Valid @RequestBody SystemSettingRequest request) {
        log.info("Request update system setting '{}' to '{}'", key, request.getValue());
        systemSettingService.updateSetting(key, request.getValue());
        return ResponseEntity.ok(ApiResponse.success("Cập nhật cấu hình thành công", null));
    }

    @PostMapping("/batch")
    public ResponseEntity<ApiResponse<Void>> updateSettings(
            @Valid @RequestBody List<SystemSettingRequest> requests) {
        log.info("Request batch update for {} system settings", requests.size());
        systemSettingService.updateSettings(requests);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật nhóm cấu hình thành công", null));
    }
}
