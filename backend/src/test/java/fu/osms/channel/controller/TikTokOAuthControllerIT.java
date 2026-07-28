package fu.osms.channel.controller;

import fu.osms.auth.repository.UserRepository;
import fu.osms.auth.security.JwtService;
import fu.osms.auth.service.AuthService;
import fu.osms.auth.service.UserService;
import fu.osms.channel.service.ChannelConnectionLogService;
import fu.osms.channel.service.ChannelService;
import fu.osms.sync.tiktok.TikTokChannelConnectionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import fu.osms.channel.dto.response.ChannelResponse;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;

/**
 * Slice tests for TikTokOAuthController.
 */
@WebMvcTest(
        controllers = TikTokOAuthController.class,
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
class TikTokOAuthControllerIT {

    @Autowired
    MockMvc mvc;

    @MockBean
    TikTokChannelConnectionService tikTokChannelConnectionService;

    @MockBean
    ChannelConnectionLogService channelConnectionLogService;

    @MockBean
    ChannelService channelService;

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
    void callback_withValidCode_redirectsToSuccess() throws Exception {
        UUID channelId = UUID.randomUUID();
        ChannelResponse channelResponse = ChannelResponse.builder()
                .id(channelId)
                .platform(PlatformType.TIKTOK)
                .displayName("TikTok Shop")
                .build();

        when(tikTokChannelConnectionService.connect(anyString(), anyString()))
                .thenReturn(channelResponse);

        mvc.perform(get("/api/channels/tiktok/callback")
                        .param("code", "valid_auth_code")
                        .param("state", "test_state"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/channels?success=tiktok_connected"));
    }

    @Test
    void callback_withErrorParam_redirectsToError() throws Exception {
        doNothing().when(channelConnectionLogService).logFailure(
                any(), any(), anyString(), anyString(), any());

        mvc.perform(get("/api/channels/tiktok/callback")
                        .param("error", "user_denied")
                        .param("state", "test_state"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/channels?error=tiktok_oauth_failed"));
    }

    @Test
    void callback_withMissingCode_redirectsToError() throws Exception {
        doNothing().when(channelConnectionLogService).logFailure(
                any(), any(), anyString(), anyString(), any());

        mvc.perform(get("/api/channels/tiktok/callback")
                        .param("state", "test_state"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/channels?error=tiktok_oauth_failed"));
    }

    @Test
    void callback_withConnectionFailure_redirectsToError() throws Exception {
        when(tikTokChannelConnectionService.connect(anyString(), anyString()))
                .thenThrow(new RuntimeException("Connection failed"));

        doNothing().when(channelConnectionLogService).logFailure(
                any(), any(), anyString(), anyString(), any());

        mvc.perform(get("/api/channels/tiktok/callback")
                        .param("code", "auth_code")
                        .param("state", "test_state"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/channels?error=tiktok_oauth_failed"));
    }

    @Test
    void callback_withIdentityConflict_redirectsToConflict() throws Exception {
        when(tikTokChannelConnectionService.connect(anyString(), anyString()))
                .thenThrow(new AppException(ErrorCode.CHANNEL_IDENTITY_CONFLICT, "Channel already exists"));

        doNothing().when(channelConnectionLogService).logFailure(
                any(), any(), anyString(), anyString(), any());

        mvc.perform(get("/api/channels/tiktok/callback")
                        .param("code", "auth_code")
                        .param("state", "test_state"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/channels?error=channel_identity_conflict"));
    }

    @Test
    void callback_withGenericException_redirectsToError() throws Exception {
        when(tikTokChannelConnectionService.connect(anyString(), anyString()))
                .thenThrow(new RuntimeException("Unexpected error"));

        doNothing().when(channelConnectionLogService).logFailure(
                any(), any(), anyString(), anyString(), any());

        mvc.perform(get("/api/channels/tiktok/callback")
                        .param("code", "auth_code")
                        .param("state", "test_state"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/channels?error=tiktok_oauth_failed"));
    }
}
