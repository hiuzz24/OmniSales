package fu.osms.channel.controller;

import fu.osms.auth.repository.UserRepository;
import fu.osms.auth.security.JwtService;
import fu.osms.auth.service.AuthService;
import fu.osms.auth.service.UserService;
import fu.osms.channel.service.ChannelConnectionLogService;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.sync.lazada.service.LazadaChannelConnectionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice tests for LazadaOAuthController.
 */
@WebMvcTest(
        controllers = LazadaOAuthController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {
                        fu.osms.auth.security.JwtAuthenticationFilter.class,
                        fu.osms.config.ApiUsageFilter.class
                }
        )
)
@TestPropertySource(properties = "app.frontend-url=https://test.example.com")
@AutoConfigureMockMvc(addFilters = false)
class LazadaOAuthControllerIT {

    @Autowired
    MockMvc mvc;

    @MockBean
    LazadaChannelConnectionService lazadaChannelConnectionService;

    @MockBean
    ChannelConnectionLogService channelConnectionLogService;

    @MockBean
    JwtService jwtService;

    @MockBean
    UserRepository userRepository;

    @MockBean
    UserService userService;

    @MockBean
    AuthService authService;

    @MockBean
    org.springframework.security.core.userdetails.UserDetailsService userDetailsService;

    @Test
    void authorize_returnsAuthorizationUrl() throws Exception {
        when(lazadaChannelConnectionService.buildAuthorizationUrl())
                .thenReturn("https://auth.lazada.com/rest?client_id=test");

        mvc.perform(get("/api/channels/lazada/authorize")
                        .accept(org.springframework.http.MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.url").value("https://auth.lazada.com/rest?client_id=test"));
    }

    @Test
    void authorize_withInvalidConfig_returnsBadRequest() throws Exception {
        when(lazadaChannelConnectionService.buildAuthorizationUrl())
                .thenThrow(new IllegalStateException("Missing Lazada OAuth configuration"));

        mvc.perform(get("/api/channels/lazada/authorize")
                        .accept(org.springframework.http.MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Missing Lazada OAuth configuration"));
    }

    @Test
    void callback_withValidCode_redirectsToSuccess() throws Exception {
        mvc.perform(get("/api/channels/lazada/callback")
                        .param("code", "valid_code"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/channels?success=lazada_connected"));
    }

    @Test
    void callback_withErrorParam_redirectsToError() throws Exception {
        doNothing().when(channelConnectionLogService).logFailure(
                any(), any(), anyString(), anyString(), any());

        mvc.perform(get("/api/channels/lazada/callback")
                        .param("error", "user_denied"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/channels?error=user_denied"));
    }

    @Test
    void callback_withMissingCode_redirectsToError() throws Exception {
        doNothing().when(channelConnectionLogService).logFailure(
                any(), any(), anyString(), anyString(), any());

        mvc.perform(get("/api/channels/lazada/callback"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/channels?error=missing_code"));
    }

    @Test
    void callback_withNoAccessToken_redirectsToError() throws Exception {
        when(lazadaChannelConnectionService.connect("invalid_code"))
                .thenThrow(new IllegalStateException("Missing access token"));

        doNothing().when(channelConnectionLogService).logFailure(
                any(), any(), anyString(), anyString(), any());

        mvc.perform(get("/api/channels/lazada/callback")
                        .param("code", "invalid_code"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/channels?error=connection_failed"));
    }

    @Test
    void callback_withGenericException_redirectsToError() throws Exception {
        when(lazadaChannelConnectionService.connect("code"))
                .thenThrow(new RuntimeException("Unexpected error"));

        doNothing().when(channelConnectionLogService).logFailure(
                any(), any(), anyString(), anyString(), any());

        mvc.perform(get("/api/channels/lazada/callback")
                        .param("code", "code"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/channels?error=connection_failed"));
    }

    @Test
    void callback_withChannelConflict_redirectsToConflict() throws Exception {
        when(lazadaChannelConnectionService.connect("code"))
                .thenThrow(new AppException(ErrorCode.CHANNEL_IDENTITY_CONFLICT, "Channel already exists"));

        mvc.perform(get("/api/channels/lazada/callback")
                        .param("code", "code"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/channels?error=channel_identity_conflict"));
    }
}
