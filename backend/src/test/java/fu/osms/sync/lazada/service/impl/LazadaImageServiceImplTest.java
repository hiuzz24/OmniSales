package fu.osms.sync.lazada.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.catalog.entity.ProductImage;
import fu.osms.sync.lazada.service.LazadaApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LazadaImageServiceImplTest {

    @Mock private LazadaApiClient lazadaApiClient;

    private LazadaImageServiceImpl service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new LazadaImageServiceImpl(lazadaApiClient, objectMapper);
    }

    @Test
    @DisplayName("migrateImages — empty input → empty list, no API call")
    void migrate_emptyInput() {
        List<String> result = service.migrateImages(List.of(), "token", 100L);
        assertThat(result).isEmpty();

        List<String> nullResult = service.migrateImages(null, "token", 100L);
        assertThat(nullResult).isEmpty();
    }

    @Test
    @DisplayName("migrateImages — happy path: 2 images both uploaded, returned in order")
    void migrate_happy() {
        ProductImage img1 = ProductImage.builder().id(java.util.UUID.randomUUID()).url("https://cdn/x1.jpg").build();
        ProductImage img2 = ProductImage.builder().id(java.util.UUID.randomUUID()).url("https://cdn/x2.jpg").build();

        when(lazadaApiClient.executePost(eq("/image/migrate"), any(), eq("tok"), anyLong()))
                .thenReturn("{\"code\":\"0\",\"data\":{\"image\":{\"url\":\"https://laz-img/1\"}}}")
                .thenReturn("{\"code\":\"0\",\"data\":{\"image\":{\"url\":\"https://laz-img/2\"}}}");

        List<String> result = service.migrateImages(List.of(img1, img2), "tok", 100L);

        assertThat(result).containsExactly("https://laz-img/1", "https://laz-img/2");
    }

    @Test
    @DisplayName("migrateImages — null/blank image URLs are skipped silently")
    void migrate_skipBlankUrls() {
        ProductImage ok = ProductImage.builder().id(java.util.UUID.randomUUID()).url("https://cdn/x.jpg").build();
        ProductImage blank = ProductImage.builder().id(java.util.UUID.randomUUID()).url("  ").build();
        ProductImage nullUrl = ProductImage.builder().id(java.util.UUID.randomUUID()).url(null).build();

        when(lazadaApiClient.executePost(any(), any(), any(), anyLong()))
                .thenReturn("{\"code\":\"0\",\"data\":{\"image\":{\"url\":\"https://laz-img/1\"}}}");

        List<String> result = service.migrateImages(List.of(ok, blank, nullUrl), "tok", 100L);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("migrateImages — API error code → RuntimeException with message")
    void migrate_apiError() {
        ProductImage img = ProductImage.builder().id(java.util.UUID.randomUUID()).url("https://cdn/x.jpg").build();
        when(lazadaApiClient.executePost(any(), any(), any(), anyLong()))
                .thenReturn("{\"code\":\"500\",\"message\":\"CDN upload failed\"}");

        assertThatThrownBy(() -> service.migrateImages(List.of(img), "tok", 100L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Lazada API returned error")
                .hasMessageContaining("CDN upload failed");
    }

    @Test
    @DisplayName("migrateImages — missing image url in success response → RuntimeException")
    void migrate_missingUrl() {
        ProductImage img = ProductImage.builder().id(java.util.UUID.randomUUID()).url("https://cdn/x.jpg").build();
        when(lazadaApiClient.executePost(any(), any(), any(), anyLong()))
                .thenReturn("{\"code\":\"0\",\"data\":{}}");

        assertThatThrownBy(() -> service.migrateImages(List.of(img), "tok", 100L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Missing image url");
    }

    @Test
    @DisplayName("migrateImages — payload XML contains <Url> wrapped in CDATA")
    void migrate_payloadContainsUrl() {
        ProductImage img = ProductImage.builder().id(java.util.UUID.randomUUID()).url("https://cdn/x.jpg").build();
        when(lazadaApiClient.executePost(any(), any(), any(), anyLong()))
                .thenReturn("{\"code\":\"0\",\"data\":{\"image\":{\"url\":\"https://laz/x\"}}}");

        service.migrateImages(List.of(img), "tok", 100L);

        ArgumentCaptor<Map<String, String>> cap = ArgumentCaptor.forClass(Map.class);
        org.mockito.Mockito.verify(lazadaApiClient).executePost(eq("/image/migrate"), cap.capture(), any(), anyLong());

        String payload = cap.getValue().get("payload");
        assertThat(payload).contains("<Url>https://cdn/x.jpg</Url>");
        assertThat(payload).contains("<Request>");
    }
}
