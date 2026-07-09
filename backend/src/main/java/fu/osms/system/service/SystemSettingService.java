package fu.osms.system.service;

import fu.osms.system.dto.SystemSettingRequest;
import fu.osms.system.entity.SystemSetting;

import java.util.List;

public interface SystemSettingService {
    List<SystemSetting> getAllSettings();
    void updateSetting(String key, String value);
    void updateSettings(List<SystemSettingRequest> requests);
    
    String getString(String key, String defaultValue);
    Integer getInteger(String key, Integer defaultValue);
    Long getLong(String key, Long defaultValue);
    Boolean getBoolean(String key, Boolean defaultValue);
}
