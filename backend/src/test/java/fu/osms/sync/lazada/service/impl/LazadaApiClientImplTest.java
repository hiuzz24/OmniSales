package fu.osms.sync.lazada.service.impl;

import fu.osms.channel.token.exception.PlatformAccessTokenExpiredException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LazadaApiClientImplTest {

    @Mock private RestTemplate restTemplate;

    private LazadaApiClientImpl client;

    @BeforeEach
    void setUp() {
        client = new LazadaApiClientImpl(restTemplate);
        ReflectionTestUtils.setField(client, "appKey", "test-app-key");
        ReflectionTestUtils.setField(client, "appSecret", "test-app-secret");
        ReflectionTestUtils.setField(client, "apiUrl", "https://api.lazada.com/rest");
    }

    @Test
    @DisplayName("executePost — happy path: signs params, posts to /auth/token/create, returns body")
    void executePost_happy() {
        when(restTemplate.postForEntity(eq("https://api.lazada.com/rest/auth/token/create"),
                any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"ok\":1}", HttpStatus.OK));

        String result = client.executePost("/auth/token/create",
                Map.of("code", "abc"), "tok", Instant.now().getEpochSecond() + 3600);

        assertThat(result).isEqualTo("{\"ok\":1}");

        ArgumentCaptor<HttpEntity> cap = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(eq("https://api.lazada.com/rest/auth/token/create"),
                cap.capture(), eq(String.class));
        HttpEntity<?> entity = cap.getValue();
        MultiValueMap<String, String> body = (MultiValueMap<String, String>) entity.getBody();
        assertThat(body.getFirst("app_key")).isEqualTo("test-app-key");
        assertThat(body.getFirst("sign_method")).isEqualTo("sha256");
        assertThat(body.getFirst("access_token")).isEqualTo("tok");
        assertThat(body.getFirst("code")).isEqualTo("abc");
        assertThat(body.getFirst("sign")).isNotBlank();
    }

    @Test
    @DisplayName("executePost — null access_token omits access_token param")
    void executePost_nullTokenOmitsAccessToken() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));

        client.executePost("/some/path", new HashMap<>(), null, Instant.now().getEpochSecond() + 3600);

        ArgumentCaptor<HttpEntity> cap = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(anyString(), cap.capture(), eq(String.class));
        MultiValueMap<String, String> body = (MultiValueMap<String, String>) cap.getValue().getBody();
        assertThat(body.getFirst("access_token")).isNull();
    }

    @Test
    @DisplayName("executePost — expired token → TokenExpiredException")
    void executePost_tokenExpiredThrows() {
        Long expired = Instant.now().getEpochSecond() - 10;

        assertThatThrownBy(() -> client.executePost("/x", Map.of(), "tok", expired))
                .isInstanceOf(PlatformAccessTokenExpiredException.class)
                .hasMessageContaining("expired");
    }

    @Test
    @DisplayName("executePost — RestClient error → RuntimeException with body")
    void executePost_restErrorWrapped() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientResponseException("server error", HttpStatus.BAD_REQUEST,
                        "BadRequest", null, "{\"error\":\"bad\"}".getBytes(), null));

        assertThatThrownBy(() -> client.executePost("/x", Map.of(), "tok", Instant.now().getEpochSecond() + 3600))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Lazada API Error")
                .hasMessageContaining("bad");
    }

    @Test
    @DisplayName("executePost — baseUrl override uses custom URL")
    void executePost_customBaseUrl() {
        when(restTemplate.postForEntity(eq("https://staging.lazada.com/rest/x"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));

        String result = client.executePost("/x", Map.of(), "tok", Instant.now().getEpochSecond() + 3600,
                "https://staging.lazada.com/rest");

        assertThat(result).isEqualTo("ok");
    }

    @Test
    @DisplayName("executeGet — encodes params into query string and returns body")
    void executeGet_happy() {
        when(restTemplate.getForEntity(any(java.net.URI.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("ok-get", HttpStatus.OK));

        String result = client.executeGet("/orders/get", Map.of("limit", "10"),
                "tok", Instant.now().getEpochSecond() + 3600);

        assertThat(result).isEqualTo("ok-get");
        ArgumentCaptor<java.net.URI> cap = ArgumentCaptor.forClass(java.net.URI.class);
        verify(restTemplate).getForEntity(cap.capture(), eq(String.class));
        String url = cap.getValue().toString();
        assertThat(url).contains("/orders/get");
        assertThat(url).contains("app_key=test-app-key");
        assertThat(url).contains("sign_method=sha256");
        assertThat(url).contains("access_token=tok");
        assertThat(url).contains("limit=10");
        assertThat(url).contains("sign=");
    }
}
