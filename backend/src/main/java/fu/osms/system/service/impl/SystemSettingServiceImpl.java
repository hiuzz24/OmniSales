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
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemSettingServiceImpl implements SystemSettingService {

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
        SystemSetting setting = systemSettingRepository.findById(key)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy cấu hình với mã: " + key));

        log.info("Updating system setting '{}' from '{}' to '{}'", key, setting.getValue(), value);
        setting.setValue(value);
        systemSettingRepository.save(setting);
        
        // Update in-memory cache
        settingsCache.put(key, value);
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
}
