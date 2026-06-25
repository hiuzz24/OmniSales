package fu.osms.sync.lazada.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.catalog.entity.ProductImage;
import fu.osms.sync.lazada.service.LazadaApiClient;
import fu.osms.sync.lazada.service.LazadaImageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
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
                String payload = buildImageMigratePayload(originalUrl);
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

    private String buildImageMigratePayload(String imageUrl) {
        try {
            Document document = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .newDocument();

            Element request = document.createElement("Request");
            document.appendChild(request);

            Element image = document.createElement("Image");
            request.appendChild(image);

            Element url = document.createElement("Url");
            url.setTextContent(imageUrl);
            image.appendChild(url);

            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.INDENT, "no");

            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(writer));
            return writer.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to build Lazada image migrate payload", e);
        }
    }
}
