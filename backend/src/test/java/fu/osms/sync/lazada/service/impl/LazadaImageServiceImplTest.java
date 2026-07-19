package fu.osms.sync.lazada.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.catalog.entity.ProductImage;
import fu.osms.sync.lazada.service.LazadaAuthorizedApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LazadaImageServiceImplTest {

    @Mock private LazadaAuthorizedApiClient lazadaApiClient;

    private LazadaImageServiceImpl service;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UUID channelId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new LazadaImageServiceImpl(lazadaApiClient, objectMapper);
    }

    @Test
    @DisplayName("migrateImages — empty list returns empty result without any API call")
    void migrateImages_empty() {
        List<String> result = service.migrateImages(List.of(), channelId);

        assertThat(result).isEmpty();
        verify(lazadaApiClient, never()).executePost(any(UUID.class), anyString(), anyMap());
    }

    @Test
    @DisplayName("migrateImages — null list returns empty result without any API call")
    void migrateImages_null() {
        List<String> result = service.migrateImages(null, channelId);

        assertThat(result).isEmpty();
        verify(lazadaApiClient, never()).executePost(any(UUID.class), anyString(), anyMap());
    }

    @Test
    @DisplayName("migrateImages — single valid image posts to /image/migrate and returns the Lazada CDN URL")
    void migrateImages_singleUrl() {
        ProductImage img = ProductImage.builder()
                .id(UUID.randomUUID())
                .url("https://cdn.example/x.jpg")
                .build();
        when(lazadaApiClient.executePost(eq(channelId), eq("/image/migrate"), anyMap()))
                .thenReturn("{\"code\":\"0\",\"data\":{\"image\":{\"url\":\"https://laz-img.cdn/1\"}}}");

        List<String> result = service.migrateImages(List.of(img), channelId);

        assertThat(result).containsExactly("https://laz-img.cdn/1");
    }

    @Test
    @DisplayName("migrateImages — blank image URLs are skipped, valid URLs still migrated")
    void migrateImages_skipsBlank() {
        ProductImage blank = ProductImage.builder().id(UUID.randomUUID()).url("  ").build();
        ProductImage valid = ProductImage.builder()
                .id(UUID.randomUUID())
                .url("https://cdn.example/y.jpg")
                .build();
        when(lazadaApiClient.executePost(eq(channelId), eq("/image/migrate"), anyMap()))
                .thenReturn("{\"code\":\"0\",\"data\":{\"image\":{\"url\":\"https://laz-img.cdn/y\"}}}");

        List<String> result = service.migrateImages(List.of(blank, valid), channelId);

        assertThat(result).containsExactly("https://laz-img.cdn/y");
    }

    @Test
    @DisplayName("migrateImageUrl — blank URL throws IllegalArgumentException")
    void migrateImageUrl_blank() {
        assertThatThrownBy(() -> service.migrateImageUrl("  ", channelId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Image URL");

        verify(lazadaApiClient, never()).executePost(any(UUID.class), anyString(), anyMap());
    }

    @Test
    @DisplayName("migrateImageUrl — Lazada returns non-zero code → wrapped RuntimeException")
    void migrateImageUrl_apiError() {
        when(lazadaApiClient.executePost(eq(channelId), eq("/image/migrate"), anyMap()))
                .thenReturn("{\"code\":\"500\",\"message\":\"oops\"}");

        assertThatThrownBy(() -> service.migrateImageUrl("https://cdn.example/z.jpg", channelId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("oops");
    }

    @Test
    @DisplayName("migrateImageUrl — Lazada returns success but missing url → wrapped RuntimeException")
    void migrateImageUrl_missingUrl() {
        when(lazadaApiClient.executePost(eq(channelId), eq("/image/migrate"), anyMap()))
                .thenReturn("{\"code\":\"0\",\"data\":{}}");

        assertThatThrownBy(() -> service.migrateImageUrl("https://cdn.example/m.jpg", channelId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Missing image url");
    }
}
