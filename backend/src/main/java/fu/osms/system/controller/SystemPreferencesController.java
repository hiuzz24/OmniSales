package fu.osms.system.controller;

import fu.osms.common.dto.ApiResponse;
import fu.osms.system.service.SystemSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/system/preferences")
@RequiredArgsConstructor
public class SystemPreferencesController {

    private final SystemSettingService systemSettingService;

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPreferences() {
        Map<String, Object> preferences = new LinkedHashMap<>();
        preferences.put("systemName", systemSettingService.getString("system_name", "OmniSales"));
        preferences.put("supportEmail", systemSettingService.getString("support_email", ""));
        preferences.put("supportPhone", systemSettingService.getString("support_phone", ""));
        preferences.put("timezone", systemSettingService.getString("timezone", "Asia/Ho_Chi_Minh"));
        preferences.put("dateFormat", systemSettingService.getString("date_format", "dd/MM/yyyy"));
        preferences.put("defaultPageSize", systemSettingService.getInteger("default_page_size", 20));
        preferences.put("maintenanceMode", systemSettingService.getBoolean("maintenance_mode", false));
        preferences.put("maintenanceMessage", systemSettingService.getString("maintenance_message", ""));
        return ResponseEntity.ok(ApiResponse.success(preferences));
    }
}
