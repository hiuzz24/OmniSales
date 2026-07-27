package fu.osms.sync.tiktok.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.sync.tiktok.TikTokApiClient;
import fu.osms.sync.tiktok.dto.TikTokAuthorizedShop;
import fu.osms.sync.tiktok.dto.TikTokTokenData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TikTokOAuthServiceImpl Tests")
class TikTokOAuthServiceImplTest {

    @Mock
    private RestTemplate restTemplate;
    @Mock
    private TikTokApiClient tikTokApiClient;

    private TikTokOAuthServiceImpl service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new TikTokOAuthServiceImpl(restTemplate, objectMapper, tikTokApiClient);
        ReflectionTestUtils.setField(service, "appKey", "test-app-key");
        ReflectionTestUtils.setField(service, "appSecret", "test-app-secret");
        ReflectionTestUtils.setField(service, "tokenUrl", "https://auth.tiktok-shops.com/api/v2/token/get");
        ReflectionTestUtils.setField(service, "refreshTokenUrl", "https://auth.tiktok-shops.com/api/v2/token/refresh");
        ReflectionTestUtils.setField(service, "apiUrl", "https://open-api.tiktokglobalshop.com");
    }

    @Nested
    @DisplayName("exchangeToken")
    class ExchangeTokenTests {

        @Test
        @DisplayName("Should exchange authorization code for token")
        void shouldExchangeCodeForToken() throws Exception {
            String jsonResponse = """
                {
                    "code": 0,
                    "message": "success",
                    "data": {
                        "access_token": "test_access_token",
                        "refresh_token": "test_refresh_token",
                        "expires_in": 3600
                    }
                }
                """;
            when(restTemplate.getForObject(anyString(), eq(String.class)))
                    .thenReturn(jsonResponse);

            TikTokTokenData result = service.exchangeToken("auth_code_123");

            assertThat(result).isNotNull();
            assertThat(result.getAccessToken()).isEqualTo("test_access_token");
            assertThat(result.getRefreshToken()).isEqualTo("test_refresh_token");
        }

        @Test
        @DisplayName("Should throw when API returns error")
        void shouldThrowWhenApiReturnsError() {
            when(restTemplate.getForObject(anyString(), eq(String.class)))
                    .thenReturn("{\"code\":10003,\"message\":\"Invalid code\"}");

            assertThatThrownBy(() -> service.exchangeToken("invalid_code"))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("exchangeTokenAndResolveShop")
    class ExchangeTokenAndResolveShopTests {

        @Test
        @DisplayName("Should exchange token and resolve single shop")
        void shouldExchangeTokenAndResolveSingleShop() throws Exception {
            String jsonResponse = """
                {
                    "code": 0,
                    "data": {
                        "access_token": "test_token",
                        "refresh_token": "test_refresh"
                    }
                }
                """;
            when(restTemplate.getForObject(anyString(), eq(String.class)))
                    .thenReturn(jsonResponse);

            TikTokAuthorizedShop shop = TikTokAuthorizedShop.builder()
                    .shopCipher("cipher123")
                    .shopId("shop_123")
                    .shopName("Test Shop")
                    .region("VN")
                    .build();
            when(tikTokApiClient.getAuthorizedShops("test_token"))
                    .thenReturn(List.of(shop));

            TikTokTokenData result = service.exchangeTokenAndResolveShop("code");

            assertThat(result).isNotNull();
            assertThat(result.getMetadata()).containsEntry("shopCipher", "cipher123");
        }

        @Test
        @DisplayName("Should throw when multiple shops returned")
        void shouldThrowWhenMultipleShops() throws Exception {
            String jsonResponse = """
                {
                    "code": 0,
                    "data": {
                        "access_token": "test_token",
                        "refresh_token": "refresh"
                    }
                }
                """;
            when(restTemplate.getForObject(anyString(), eq(String.class)))
                    .thenReturn(jsonResponse);

            TikTokAuthorizedShop shop1 = TikTokAuthorizedShop.builder()
                    .shopCipher("c1").shopId("s1").build();
            TikTokAuthorizedShop shop2 = TikTokAuthorizedShop.builder()
                    .shopCipher("c2").shopId("s2").build();
            when(tikTokApiClient.getAuthorizedShops("test_token"))
                    .thenReturn(List.of(shop1, shop2));

            assertThatThrownBy(() -> service.exchangeTokenAndResolveShop("code"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("exactly one active shop");
        }
    }
}
