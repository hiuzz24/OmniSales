package fu.osms.sync.lazada.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.sync.lazada.service.LazadaApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LazadaOAuthServiceImplTest {

    @Mock private LazadaApiClient lazadaApiClient;

    private LazadaOAuthServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LazadaOAuthServiceImpl(lazadaApiClient, new ObjectMapper());
        ReflectionTestUtils.setField(service, "appKey", "test-app-key");
        ReflectionTestUtils.setField(service, "redirectUri", "https://test/callback");
        ReflectionTestUtils.setField(service, "authUrl", "https://auth.lazada.com/rest");
        ReflectionTestUtils.setField(service, "authApiUrl", "https://auth.lazada.com/rest");
        ReflectionTestUtils.setField(service, "apiUrl", "https://api.lazada.com/rest");
    }

    @Test
    @DisplayName("buildAuthorizationUrl — contains response_type, force_auth, redirect_uri, client_id")
    void buildAuthorizationUrl_includesRequiredParams() {
        String url = service.buildAuthorizationUrl();

        assertThat(url).contains("response_type=code");
        assertThat(url).contains("force_auth=true");
        assertThat(url).contains("redirect_uri=https://test/callback");
        assertThat(url).contains("client_id=test-app-key");
    }

    @Test
    @DisplayName("buildAuthorizationUrl — missing appKey throws IllegalStateException")
    void buildAuthorizationUrl_missingAppKey() {
        ReflectionTestUtils.setField(service, "appKey", "");

        assertThatThrownBy(() -> service.buildAuthorizationUrl())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Thiếu cấu hình Lazada OAuth");
    }

    @Test
    @DisplayName("buildAuthorizationUrl — missing redirectUri throws IllegalStateException")
    void buildAuthorizationUrl_missingRedirect() {
        ReflectionTestUtils.setField(service, "redirectUri", "");

        assertThatThrownBy(() -> service.buildAuthorizationUrl())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Thiếu cấu hình Lazada OAuth");
    }

    @Test
    @DisplayName("exchangeToken — happy path returns access/refresh + account_id from country_user_info")
    void exchangeToken_happy() {
        when(lazadaApiClient.executePost(eq("/auth/token/create"), any(), eq(null), eq(null), eq("https://api.lazada.com/rest")))
                .thenReturn("{\"code\":\"0\",\"access_token\":\"acc-1\",\"refresh_token\":\"ref-1\",\"expires_in\":3600,\"country_user_info\":[{\"seller_id\":\"S1\",\"seller_name\":\"My Shop\"}]}");

        Map<String, Object> result = service.exchangeToken("auth-code");

        assertThat(result.get("access_token")).isEqualTo("acc-1");
        assertThat(result.get("refresh_token")).isEqualTo("ref-1");
        assertThat(result.get("account_id")).isEqualTo("S1");
        assertThat(result.get("account_name")).isEqualTo("My Shop");
    }

    @Test
    @DisplayName("exchangeToken — happy path: account_id and account_name filled only when missing")
    void exchangeToken_preservesExistingAccount() {
        when(lazadaApiClient.executePost(any(), any(), any(), any(), any()))
                .thenReturn("{\"code\":\"0\",\"access_token\":\"acc\",\"account_id\":\"preset\",\"account_name\":\"preset\",\"country_user_info\":[{\"seller_id\":\"S1\"}]}");

        Map<String, Object> result = service.exchangeToken("c");

        assertThat(result.get("account_id")).isEqualTo("preset");
        assertThat(result.get("account_name")).isEqualTo("preset");
    }

    @Test
    @DisplayName("exchangeToken — API returns non-zero code → wrapped RuntimeException with cause")
    void exchangeToken_apiError() {
        when(lazadaApiClient.executePost(any(), any(), any(), any(), any()))
                .thenReturn("{\"code\":\"100\",\"message\":\"invalid code\"}");

        assertThatThrownBy(() -> service.exchangeToken("c"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to exchange Lazada token")
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("exchangeToken — missing access_token → wrapped RuntimeException with cause")
    void exchangeToken_missingAccessToken() {
        when(lazadaApiClient.executePost(any(), any(), any(), any(), any()))
                .thenReturn("{\"code\":\"0\"}");

        assertThatThrownBy(() -> service.exchangeToken("c"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to exchange Lazada token")
                .hasCauseInstanceOf(IllegalStateException.class);
    }
}
