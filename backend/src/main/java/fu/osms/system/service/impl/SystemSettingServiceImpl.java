package fu.osms.system.service.impl;

import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.system.dto.SystemSettingRequest;
import fu.osms.system.entity.SystemSetting;
import fu.osms.system.repository.SystemSettingRepository;
import fu.osms.system.service.SystemSettingService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemSettingServiceImpl implements SystemSettingService {

    private static final Set<String> BOOLEAN_SETTINGS = Set.of(
            "notification_order_enabled",
            "notification_return_enabled",
            "notification_low_stock_enabled",
            "notification_sync_failure_enabled",
            "notification_channel_disconnected_enabled",
            "notification_email_enabled",
            "password_require_uppercase",
            "password_require_lowercase",
            "password_require_number",
            "password_require_special_character",
            "maintenance_mode",
            "backup_schedule_enabled"
    );

    private static final Set<String> POSITIVE_INTEGER_SETTINGS = Set.of(
            "low_stock_repeat_hours",
            "notification_retention_days",
            "max_failed_login_attempts",
            "account_lock_minutes",
            "access_token_expiration_minutes",
            "refresh_token_expiration_days",
            "password_min_length",
            "default_page_size",
            "audit_log_retention_days"
    );

    private final SystemSettingRepository systemSettingRepository;
    private final ConcurrentHashMap<String, String> settingsCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("Loading system settings into in-memory cache...");
        refreshCache();
    }

    private void refreshCache() {
        try {
            settingsCache.clear();
            List<SystemSetting> settings = systemSettingRepository.findAll();
            for (SystemSetting setting : settings) {
                settingsCache.put(setting.getKey(), setting.getValue());
            }
            applyRuntimeSetting("timezone", settingsCache.get("timezone"));
            log.info("Successfully cached {} system settings", settingsCache.size());
        } catch (Exception e) {
            log.error("Failed to load system settings from database: {}", e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<SystemSetting> getAllSettings() {
        return systemSettingRepository.findAll();
    }

    @Override
    @Transactional
    public void updateSetting(String key, String value) {
        validateValue(key, value);
        SystemSetting setting = systemSettingRepository.findById(key)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy cấu hình với mã: " + key));

        log.info("Updating system setting '{}' from '{}' to '{}'", key, setting.getValue(), value);
        setting.setValue(value);
        systemSettingRepository.save(setting);
        
        // Update in-memory cache
        settingsCache.put(key, value);
        applyRuntimeSetting(key, value);
    }

    @Override
    @Transactional
    public void updateSettings(List<SystemSettingRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return;
        }
        
        log.info("Performing batch update of {} system settings", requests.size());
        for (SystemSettingRequest req : requests) {
            if (req.getKey() != null && req.getValue() != null) {
                updateSetting(req.getKey(), req.getValue());
            }
        }
    }

    @Override
    public String getString(String key, String defaultValue) {
        String value = settingsCache.get(key);
        if (value == null) {
            // Try to load from DB just in case cache was missed or initialized before seeding
            OptionalSystemSettingFallback(key);
            value = settingsCache.get(key);
        }
        return value != null ? value : defaultValue;
    }

    private void OptionalSystemSettingFallback(String key) {
        systemSettingRepository.findById(key).ifPresent(setting -> 
            settingsCache.put(setting.getKey(), setting.getValue())
        );
    }

    @Override
    public Integer getInteger(String key, Integer defaultValue) {
        String value = getString(key, null);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.error("Failed to parse integer setting '{}': {}", key, value);
            return defaultValue;
        }
    }

    @Override
    public Long getLong(String key, Long defaultValue) {
        String value = getString(key, null);
        if (value == null) return defaultValue;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            log.error("Failed to parse long setting '{}': {}", key, value);
            return defaultValue;
        }
    }

    @Override
    public Boolean getBoolean(String key, Boolean defaultValue) {
        String value = getString(key, null);
        if (value == null) return defaultValue;
        return Boolean.parseBoolean(value);
    }

    private void validateValue(String key, String value) {
        if (BOOLEAN_SETTINGS.contains(key)
                && !("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value))) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Giá trị cấu hình " + key + " phải là true hoặc false");
        }
        if (POSITIVE_INTEGER_SETTINGS.contains(key)) {
            try {
                if (Integer.parseInt(value) <= 0) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException exception) {
                throw new AppException(ErrorCode.VALIDATION_FAILED,
                        "Giá trị cấu hình " + key + " phải là số nguyên lớn hơn 0");
            }
        }
        if ("password_expiration_days".equals(key)) {
            try {
                if (Integer.parseInt(value) < 0) throw new NumberFormatException();
            } catch (NumberFormatException exception) {
                throw new AppException(ErrorCode.VALIDATION_FAILED,
                        "Số ngày hết hạn mật khẩu phải là số nguyên lớn hơn hoặc bằng 0");
            }
        }
        if ("password_min_length".equals(key)) {
            int length = Integer.parseInt(value);
            if (length < 6 || length > 128) {
                throw new AppException(ErrorCode.VALIDATION_FAILED,
                        "Độ dài mật khẩu tối thiểu phải từ 6 đến 128 ký tự");
            }
        }
        if ("default_page_size".equals(key)) {
            int size = Integer.parseInt(value);
            if (size < 10 || size > 100) {
                throw new AppException(ErrorCode.VALIDATION_FAILED,
                        "Số bản ghi mỗi trang phải từ 10 đến 100");
            }
        }
        if ("date_format".equals(key)
                && !Set.of("dd/MM/yyyy", "MM/dd/yyyy", "yyyy-MM-dd").contains(value)) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Định dạng ngày tháng không được hỗ trợ");
        }
        if ("timezone".equals(key)) {
            try {
                ZoneId.of(value);
            } catch (DateTimeException exception) {
                throw new AppException(ErrorCode.VALIDATION_FAILED, "Múi giờ không hợp lệ");
            }
        }
        if ("support_email".equals(key)
                && !value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Email hỗ trợ không hợp lệ");
        }
    }

    private void applyRuntimeSetting(String key, String value) {
        if ("timezone".equals(key) && value != null) {
            TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of(value)));
            log.info("Applied system timezone: {}", value);
        }
    }
}
