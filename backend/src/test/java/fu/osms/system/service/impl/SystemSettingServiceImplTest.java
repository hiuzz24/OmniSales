package fu.osms.system.service.impl;

import fu.osms.common.exception.AppException;
import fu.osms.system.dto.SystemSettingRequest;
import fu.osms.system.entity.SystemSetting;
import fu.osms.system.repository.SystemSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SystemSettingServiceImpl - Unit Tests")
class SystemSettingServiceImplTest {

    @Mock
    private SystemSettingRepository systemSettingRepository;

    @InjectMocks
    private SystemSettingServiceImpl systemSettingService;

    private ConcurrentHashMap<String, String> testCache;

    @BeforeEach
    void setUp() {
        testCache = new ConcurrentHashMap<>();
        ReflectionTestUtils.setField(systemSettingService, "settingsCache", testCache);
    }

    @Test
    @DisplayName("getAllSettings - returns list from repository")
    void getAllSettings_returnsList() {
        SystemSetting sampleSetting = SystemSetting.builder()
                .key("app.name")
                .value("OmniSales")
                .description("Application name")
                .category("general")
                .updatedAt(OffsetDateTime.now())
                .build();

        when(systemSettingRepository.findAll()).thenReturn(List.of(sampleSetting));

        List<SystemSetting> result = systemSettingService.getAllSettings();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getKey()).isEqualTo("app.name");
        verify(systemSettingRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("updateSetting - updates value and cache")
    void updateSetting_updatesValueAndCache() {
        SystemSetting sampleSetting = SystemSetting.builder()
                .key("app.name")
                .value("OmniSales")
                .category("general")
                .build();

        when(systemSettingRepository.findById("app.name")).thenReturn(Optional.of(sampleSetting));
        when(systemSettingRepository.save(any(SystemSetting.class))).thenReturn(sampleSetting);

        systemSettingService.updateSetting("app.name", "NewOmniSales");

        assertThat(sampleSetting.getValue()).isEqualTo("NewOmniSales");
        assertThat(testCache.get("app.name")).isEqualTo("NewOmniSales");
        verify(systemSettingRepository, times(1)).save(sampleSetting);
    }

    @Test
    @DisplayName("updateSetting - throws AppException when key not found")
    void updateSetting_notFound_throwsException() {
        when(systemSettingRepository.findById("nonexistent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> systemSettingService.updateSetting("nonexistent", "value"))
                .isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("updateSettings_batch - updates multiple settings")
    void updateSettings_batch_updatesMultiple() {
        SystemSetting setting1 = SystemSetting.builder()
                .key("app.name")
                .value("OldName")
                .category("general")
                .build();

        SystemSetting setting2 = SystemSetting.builder()
                .key("app.version")
                .value("1.0.0")
                .category("general")
                .build();

        when(systemSettingRepository.findById("app.name")).thenReturn(Optional.of(setting1));
        when(systemSettingRepository.findById("app.version")).thenReturn(Optional.of(setting2));
        when(systemSettingRepository.save(any(SystemSetting.class))).thenAnswer(inv -> inv.getArgument(0));

        List<SystemSettingRequest> requests = List.of(
                new SystemSettingRequest("app.name", "UpdatedName"),
                new SystemSettingRequest("app.version", "2.0.0")
        );

        systemSettingService.updateSettings(requests);

        assertThat(setting1.getValue()).isEqualTo("UpdatedName");
        assertThat(setting2.getValue()).isEqualTo("2.0.0");
        verify(systemSettingRepository, times(2)).save(any(SystemSetting.class));
    }

    @Test
    @DisplayName("updateSettings - empty list returns early")
    void updateSettings_emptyList_returnsEarly() {
        systemSettingService.updateSettings(List.of());

        verify(systemSettingRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateSettings - null list returns early")
    void updateSettings_nullList_returnsEarly() {
        systemSettingService.updateSettings(null);

        verify(systemSettingRepository, never()).save(any());
    }

    @Test
    @DisplayName("getString - found in cache returns value")
    void getString_foundInCache() {
        testCache.put("app.name", "OmniSales");

        String result = systemSettingService.getString("app.name", "default");

        assertThat(result).isEqualTo("OmniSales");
        verify(systemSettingRepository, never()).findById(any());
    }

    @Test
    @DisplayName("getString - not found in cache, fallback to DB")
    void getString_fallbackToDb() {
        SystemSetting setting = SystemSetting.builder()
                .key("app.name")
                .value("OmniSales")
                .category("general")
                .build();

        when(systemSettingRepository.findById("app.name")).thenReturn(Optional.of(setting));

        String result = systemSettingService.getString("app.name", "default");

        assertThat(result).isEqualTo("OmniSales");
        verify(systemSettingRepository).findById("app.name");
    }

    @Test
    @DisplayName("getString - not found returns default")
    void getString_notFound_returnsDefault() {
        when(systemSettingRepository.findById("nonexistent")).thenReturn(Optional.empty());

        String result = systemSettingService.getString("nonexistent", "default");

        assertThat(result).isEqualTo("default");
    }

    @Test
    @DisplayName("getInteger - parses integer correctly")
    void getInteger_parsing() {
        testCache.put("app.timeout", "42");

        Integer result = systemSettingService.getInteger("app.timeout", 0);

        assertThat(result).isEqualTo(42);
    }

    @Test
    @DisplayName("getInteger - not found returns default")
    void getInteger_notFound_returnsDefault() {
        when(systemSettingRepository.findById("app.timeout")).thenReturn(Optional.empty());

        Integer result = systemSettingService.getInteger("app.timeout", 100);

        assertThat(result).isEqualTo(100);
    }

    @Test
    @DisplayName("getInteger - invalid format returns default")
    void getInteger_invalidFormat_returnsDefault() {
        testCache.put("app.timeout", "not-a-number");

        Integer result = systemSettingService.getInteger("app.timeout", 100);

        assertThat(result).isEqualTo(100);
    }

    @Test
    @DisplayName("getBoolean - parses boolean correctly")
    void getBoolean_parsing() {
        testCache.put("app.debug", "true");

        Boolean result = systemSettingService.getBoolean("app.debug", false);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("getBoolean - false value parsed correctly")
    void getBoolean_falseValue() {
        testCache.put("app.debug", "false");

        Boolean result = systemSettingService.getBoolean("app.debug", true);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("getBoolean - not found returns default")
    void getBoolean_notFound_returnsDefault() {
        when(systemSettingRepository.findById("app.debug")).thenReturn(Optional.empty());

        Boolean result = systemSettingService.getBoolean("app.debug", true);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("getLong - parses long correctly")
    void getLong_parsing() {
        testCache.put("app.max", "9223372036854775807");

        Long result = systemSettingService.getLong("app.max", 0L);

        assertThat(result).isEqualTo(9223372036854775807L);
    }

    @Test
    @DisplayName("getLong - not found returns default")
    void getLong_notFound_returnsDefault() {
        when(systemSettingRepository.findById("app.max")).thenReturn(Optional.empty());

        Long result = systemSettingService.getLong("app.max", 999L);

        assertThat(result).isEqualTo(999L);
    }

    @Test
    @DisplayName("getLong - invalid format returns default")
    void getLong_invalidFormat_returnsDefault() {
        testCache.put("app.max", "not-a-long");

        Long result = systemSettingService.getLong("app.max", 999L);

        assertThat(result).isEqualTo(999L);
    }
}
