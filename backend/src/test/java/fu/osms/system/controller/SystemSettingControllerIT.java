package fu.osms.system.controller;

import fu.osms.auth.repository.UserRepository;
import fu.osms.auth.security.JwtAuthenticationFilter;
import fu.osms.auth.security.JwtService;
import fu.osms.auth.service.AuthService;
import fu.osms.auth.service.UserService;
import fu.osms.config.ApiUsageFilter;
import fu.osms.system.entity.SystemSetting;
import fu.osms.system.service.SystemSettingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for {@link SystemSettingController}.
 *
 * <p>The role-based authorization (@PreAuthorize hasRole SYSTEM_ADMIN)
 * is verified separately in the full E2E backend tests; here we focus
 * on controller-level happy paths and validation.</p>
 */
@WebMvcTest(
        controllers = SystemSettingController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, ApiUsageFilter.class}
        )
)
@AutoConfigureMockMvc(addFilters = false)
class SystemSettingControllerIT {

    @Autowired MockMvc mvc;
    @MockitoBean SystemSettingService systemSettingService;
    @MockitoBean JwtService jwtService;
    @MockitoBean UserRepository userRepository;
    @MockitoBean UserService userService;
    @MockitoBean AuthService authService;
    @MockitoBean org.springframework.security.core.userdetails.UserDetailsService userDetailsService;

    private SystemSetting sampleSetting() {
        return SystemSetting.builder()
                .key("default_reorder_level")
                .value("10")
                .category("INVENTORY")
                .description("Default reorder level")
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    @Test
    void getAll_returns200() throws Exception {
        when(systemSettingService.getAllSettings()).thenReturn(List.of(sampleSetting()));

        mvc.perform(get("/api/admin/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].key").value("default_reorder_level"));
    }

    @Test
    void updateSetting_returns200() throws Exception {
        doNothing().when(systemSettingService).updateSetting("default_reorder_level", "20");

        mvc.perform(put("/api/admin/settings/{key}", "default_reorder_level")
                        .contentType("application/json")
                        .content("{\"value\":\"20\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void updateSetting_returns400_whenValueBlank() throws Exception {
        mvc.perform(put("/api/admin/settings/{key}", "default_reorder_level")
                        .contentType("application/json")
                        .content("{\"value\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void batchUpdate_returns200() throws Exception {
        doNothing().when(systemSettingService).updateSettings(org.mockito.ArgumentMatchers.any());

        mvc.perform(post("/api/admin/settings/batch")
                        .contentType("application/json")
                        .content("[{\"key\":\"a\",\"value\":\"1\"},{\"key\":\"b\",\"value\":\"2\"}]"))
                .andExpect(status().isOk());
    }
}