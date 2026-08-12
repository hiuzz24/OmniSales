package fu.osms.channel.controller;

import fu.osms.auth.repository.UserRepository;
import fu.osms.auth.security.JwtService;
import fu.osms.auth.service.AuthService;
import fu.osms.auth.service.UserService;
import fu.osms.channel.service.ChannelConnectionLogService;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.sync.shopify.ShopifyChannelConnectionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice tests for ShopifyOAuthController.
 */
@WebMvcTest(
        controllers = ShopifyOAuthController.class,
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
class ShopifyOAuthControllerIT {

    @Autowired
    MockMvc mvc;

    @MockBean
    ShopifyChannelConnectionService shopifyChannelConnectionService;

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
    void authorize_withValidShop_returnsAuthorizationUrl() throws Exception {
        when(shopifyChannelConnectionService.buildAuthorizationUrl("demo"))
                .thenReturn("https://demo.myshopify.com/admin/oauth/authorize?client_id=test");

        mvc.perform(get("/api/channels/shopify/authorize")
                        .param("shop", "demo")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.url").value("https://demo.myshopify.com/admin/oauth/authorize?client_id=test"));
    }

    @Test
    void authorize_withInvalidShop_returnsBadRequest() throws Exception {
        when(shopifyChannelConnectionService.buildAuthorizationUrl("invalid"))
                .thenThrow(new IllegalArgumentException("Invalid shop name"));

        mvc.perform(get("/api/channels/shopify/authorize")
                        .param("shop", "invalid")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void callback_withValidCodeAndShop_redirectsToSuccess() throws Exception {
        mvc.perform(get("/api/channels/shopify/callback")
                        .param("code", "valid_code")
                        .param("shop", "demo.myshopify.com")
                        .param("state", "test_state"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://test.example.com/channels?success=true"));
    }

    @Test
    void callback_withMissingReadLocationsScope_returnsError() throws Exception {
        when(shopifyChannelConnectionService.connect("demo.myshopify.com", "valid_code"))
                .thenThrow(new IllegalStateException("Missing read_locations"));

        mvc.perform(get("/api/channels/shopify/callback")
                        .param("code", "valid_code")
                        .param("shop", "demo.myshopify.com"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://test.example.com/channels?error=oauth_failed"));
    }

    @Test
    void callback_withChannelConflict_redirectsToConflict() throws Exception {
        when(shopifyChannelConnectionService.connect("demo.myshopify.com", "valid_code"))
                .thenThrow(new AppException(ErrorCode.CHANNEL_IDENTITY_CONFLICT, "Channel already exists"));

        doNothing().when(channelConnectionLogService).logFailure(any(), any(), anyString(), anyString(), any());

        mvc.perform(get("/api/channels/shopify/callback")
                        .param("code", "valid_code")
                        .param("shop", "demo.myshopify.com"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://test.example.com/channels?error=channel_identity_conflict"));
    }

    @Test
    void callback_withGenericError_redirectsToError() throws Exception {
        when(shopifyChannelConnectionService.connect("demo.myshopify.com", "invalid_code"))
                .thenThrow(new RuntimeException("Token exchange failed"));

        doNothing().when(channelConnectionLogService).logFailure(any(), any(), anyString(), anyString(), any());

        mvc.perform(get("/api/channels/shopify/callback")
                        .param("code", "invalid_code")
                        .param("shop", "demo.myshopify.com"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://test.example.com/channels?error=oauth_failed"));
    }
}
