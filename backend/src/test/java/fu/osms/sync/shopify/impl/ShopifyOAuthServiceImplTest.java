package fu.osms.sync.shopify.impl;

import fu.osms.sync.shopify.ShopifyShopDomainNormalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ShopifyOAuthServiceImpl Tests")
class ShopifyOAuthServiceImplTest {

    @Mock
    private RestTemplate restTemplate;
    @Mock
    private ShopifyShopDomainNormalizer shopDomainNormalizer;

    private ShopifyOAuthServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ShopifyOAuthServiceImpl(restTemplate, shopDomainNormalizer);
        ReflectionTestUtils.setField(service, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(service, "apiSecret", "test-api-secret");
        ReflectionTestUtils.setField(service, "redirectUri", "http://localhost/callback");
        ReflectionTestUtils.setField(service, "scopes", "read_products,write_products");
    }

    @Nested
    @DisplayName("buildAuthorizationUrl")
    class BuildAuthorizationUrlTests {

        @Test
        @DisplayName("Should return valid authorization URL with required params")
        void shouldReturnValidAuthorizationUrl() {
            when(shopDomainNormalizer.normalizeHandle("demo")).thenReturn("demo");
            when(shopDomainNormalizer.canonicalDomain("demo")).thenReturn("demo.myshopify.com");

            String result = service.buildAuthorizationUrl("demo");

            assertThat(result).isNotBlank();
            assertThat(result).contains("demo.myshopify.com/admin/oauth/authorize");
            assertThat(result).contains("client_id=test-api-key");
            assertThat(result).contains("redirect_uri=http://localhost/callback");
            assertThat(result).contains("scope=read_products,write_products");
            assertThat(result).contains("state=");
        }

        @Test
        @DisplayName("Should throw when API key is missing")
        void shouldThrowWhenApiKeyMissing() {
            ReflectionTestUtils.setField(service, "apiKey", "");

            assertThatThrownBy(() -> service.buildAuthorizationUrl("demo"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Missing Shopify OAuth configuration");
        }
    }

    @Nested
    @DisplayName("exchangeCodeForToken")
    class ExchangeCodeForTokenTests {

        @Test
        @DisplayName("Should return access token from Shopify response")
        void shouldReturnAccessToken() {
            when(shopDomainNormalizer.normalizeHandle("demo.myshopify.com")).thenReturn("demo.myshopify.com");
            when(shopDomainNormalizer.canonicalDomain("demo.myshopify.com")).thenReturn("demo.myshopify.com");

            Map<String, Object> shopifyResponse = Map.of(
                    "access_token", "shpat_test_abc123",
                    "scope", "read_products"
            );
            when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(shopifyResponse);

            String token = service.exchangeCodeForToken("demo.myshopify.com", "auth_code_123");

            assertThat(token).isEqualTo("shpat_test_abc123");
        }

        @Test
        @DisplayName("Should throw when response has no access_token")
        void shouldThrowWhenNoAccessToken() {
            when(shopDomainNormalizer.normalizeHandle("demo.myshopify.com")).thenReturn("demo.myshopify.com");
            when(shopDomainNormalizer.canonicalDomain("demo.myshopify.com")).thenReturn("demo.myshopify.com");

            when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(Map.of("error", "invalid_code"));

            assertThatThrownBy(() -> service.exchangeCodeForToken("demo.myshopify.com", "invalid_code"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("did not return an access_token");
        }

        @Test
        @DisplayName("Should throw when response is null")
        void shouldThrowWhenResponseNull() {
            when(shopDomainNormalizer.normalizeHandle("demo.myshopify.com")).thenReturn("demo.myshopify.com");
            when(shopDomainNormalizer.canonicalDomain("demo.myshopify.com")).thenReturn("demo.myshopify.com");

            when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(null);

            assertThatThrownBy(() -> service.exchangeCodeForToken("demo.myshopify.com", "code"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("did not return an access_token");
        }
    }
}
