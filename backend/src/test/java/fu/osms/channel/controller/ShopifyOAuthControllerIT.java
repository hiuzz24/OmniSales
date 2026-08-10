package fu.osms.channel.controller;

import fu.osms.auth.repository.UserRepository;
import fu.osms.auth.security.JwtService;
import fu.osms.auth.service.AuthService;
import fu.osms.auth.service.UserService;
import fu.osms.channel.service.ChannelConnectionLogService;
import fu.osms.channel.service.ChannelService;
import fu.osms.common.dto.ApiResponse;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.sync.shopify.ShopifyApiClient;
import fu.osms.sync.shopify.ShopifyOAuthService;
import fu.osms.sync.shopify.ShopifyShopDomainNormalizer;
import fu.osms.channel.dto.response.ChannelResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
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

    @MockitoBean
    ShopifyOAuthService shopifyOAuthService;

    @MockitoBean
    ShopifyApiClient shopifyApiClient;

    @MockitoBean
    ShopifyShopDomainNormalizer shopDomainNormalizer;

    @MockitoBean
    ChannelService channelService;

    @MockitoBean
    ChannelConnectionLogService channelConnectionLogService;

    @MockitoBean
    JwtService jwtService;

    @MockitoBean
    UserRepository userRepository;

    @MockitoBean
    UserService userService;

    @MockitoBean
    AuthService authService;

    @MockitoBean
    org.springframework.security.core.userdetails.UserDetailsService userDetailsService;

    @Test
    void authorize_withValidShop_returnsAuthorizationUrl() throws Exception {
        when(shopDomainNormalizer.normalizeHandle("demo")).thenReturn("demo.myshopify.com");
        when(shopifyOAuthService.buildAuthorizationUrl("demo.myshopify.com"))
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
        when(shopDomainNormalizer.normalizeHandle("invalid")).thenThrow(new IllegalArgumentException("Invalid shop name"));

        mvc.perform(get("/api/channels/shopify/authorize")
                        .param("shop", "invalid")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void callback_withValidCodeAndShop_redirectsToSuccess() throws Exception {
        UUID channelId = UUID.randomUUID();
        ChannelResponse channelResponse = ChannelResponse.builder()
                .id(channelId)
                .platform(PlatformType.SHOPIFY)
                .displayName("Demo Store")
                .build();

        when(shopDomainNormalizer.normalizeHandle("demo.myshopify.com")).thenReturn("demo.myshopify.com");
        when(shopifyOAuthService.exchangeCodeForToken("demo.myshopify.com", "valid_code"))
                .thenReturn("access_token_123");
        when(shopifyApiClient.listAccessScopes("demo.myshopify.com", "access_token_123"))
                .thenReturn(List.of("read_locations", "read_products"));
        when(channelService.connectShopify("demo.myshopify.com", "access_token_123"))
                .thenReturn(channelResponse);
        doNothing().when(channelService).registerShopifyWebhooks(anyString(), anyString(), any());

        mvc.perform(get("/api/channels/shopify/callback")
                        .param("code", "valid_code")
                        .param("shop", "demo.myshopify.com")
                        .param("state", "test_state"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://test.example.com/channels?success=true"));
    }

    @Test
    void callback_withMissingReadLocationsScope_returnsError() throws Exception {
        when(shopDomainNormalizer.normalizeHandle("demo.myshopify.com")).thenReturn("demo.myshopify.com");
        when(shopifyOAuthService.exchangeCodeForToken("demo.myshopify.com", "valid_code"))
                .thenReturn("access_token_123");
        when(shopifyApiClient.listAccessScopes("demo.myshopify.com", "access_token_123"))
                .thenReturn(List.of("read_products"));

        mvc.perform(get("/api/channels/shopify/callback")
                        .param("code", "valid_code")
                        .param("shop", "demo.myshopify.com"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://test.example.com/channels?error=oauth_failed"));
    }

    @Test
    void callback_withChannelConflict_redirectsToConflict() throws Exception {
        when(shopDomainNormalizer.normalizeHandle("demo.myshopify.com")).thenReturn("demo.myshopify.com");
        when(shopifyOAuthService.exchangeCodeForToken("demo.myshopify.com", "valid_code"))
                .thenReturn("access_token_123");
        when(shopifyApiClient.listAccessScopes("demo.myshopify.com", "access_token_123"))
                .thenReturn(List.of("read_locations"));
        when(channelService.connectShopify("demo.myshopify.com", "access_token_123"))
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
        when(shopDomainNormalizer.normalizeHandle("demo.myshopify.com")).thenReturn("demo.myshopify.com");
        when(shopifyOAuthService.exchangeCodeForToken("demo.myshopify.com", "invalid_code"))
                .thenThrow(new RuntimeException("Token exchange failed"));

        doNothing().when(channelConnectionLogService).logFailure(any(), any(), anyString(), anyString(), any());

        mvc.perform(get("/api/channels/shopify/callback")
                        .param("code", "invalid_code")
                        .param("shop", "demo.myshopify.com"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://test.example.com/channels?error=oauth_failed"));
    }
}
