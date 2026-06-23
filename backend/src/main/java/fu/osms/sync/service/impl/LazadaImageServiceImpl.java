package fu.osms.sync.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.catalog.entity.ProductImage;
import fu.osms.sync.service.LazadaApiClient;
import fu.osms.sync.service.LazadaImageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LazadaImageServiceImpl implements LazadaImageService {

    private final LazadaApiClient lazadaApiClient;
    private final ObjectMapper objectMapper;

    @Override
    public List<String> migrateImages(List<ProductImage> images, String accessToken, Long tokenExpiresAt) {
        List<String> migratedUrls = new ArrayList<>();
        
        if (images == null || images.isEmpty()) {
            return migratedUrls;
        }

        for (ProductImage image : images) {
            String originalUrl = image.getUrl();
            if (originalUrl == null || originalUrl.isBlank()) {
                continue;
            }

            try {
                String payload = "<Request><Image><Url>" + originalUrl + "</Url></Image></Request>";
                Map<String, String> params = new HashMap<>();
                params.put("payload", payload);

                String responseStr = lazadaApiClient.executePost("/image/migrate", params, accessToken, tokenExpiresAt);
                JsonNode root = objectMapper.readTree(responseStr);

                if (root.has("code") && "0".equals(root.get("code").asText())) {
                    JsonNode data = root.path("data");
                    if (data.has("image") && data.path("image").has("url")) {
                        String newUrl = data.path("image").path("url").asText();
                        migratedUrls.add(newUrl);
                        log.info("[LazadaImageService] Migrated image: {} -> {}", originalUrl, newUrl);
                    } else {
                        throw new RuntimeException("Missing image url in response");
                    }
                } else {
                    String errorMsg = root.has("message") ? root.get("message").asText() : "Unknown error";
                    throw new RuntimeException("Lazada API returned error: " + errorMsg);
                }
            } catch (Exception e) {
                log.error("[LazadaImageService] Failed to migrate image: {}", originalUrl, e);
                throw new RuntimeException("Failed to migrate image to Lazada CDN: " + originalUrl + ". Error: " + e.getMessage(), e);
            }
        }

        return migratedUrls;
    }
}
