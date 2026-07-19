package fu.osms.sync.lazada.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.catalog.entity.ProductImage;
import fu.osms.sync.lazada.service.LazadaAuthorizedApiClient;
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
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LazadaImageServiceImpl implements LazadaImageService {

    private final LazadaAuthorizedApiClient lazadaApiClient;
    private final ObjectMapper objectMapper;

    @Override
    public List<String> migrateImages(List<ProductImage> images, UUID channelId) {
        List<String> migratedUrls = new ArrayList<>();
        
        if (images == null || images.isEmpty()) {
            return migratedUrls;
        }

        for (ProductImage image : images) {
            String originalUrl = image.getUrl();
            if (originalUrl == null || originalUrl.isBlank()) {
                continue;
            }
            migratedUrls.add(migrateImageUrl(originalUrl, channelId));
        }

        return migratedUrls;
    }

    @Override
    public String migrateImageUrl(String imageUrl, UUID channelId) {
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new IllegalArgumentException("Image URL must not be blank");
        }
        try {
            String payload = buildImageMigratePayload(imageUrl);
            Map<String, String> params = new HashMap<>();
            params.put("payload", payload);

            String responseStr = lazadaApiClient.executePost(channelId, "/image/migrate", params);
            JsonNode root = objectMapper.readTree(responseStr);
            if (!root.has("code") || !"0".equals(root.get("code").asText())) {
                String errorMsg = root.has("message") ? root.get("message").asText() : "Unknown error";
                throw new RuntimeException("Lazada API returned error: " + errorMsg);
            }
            String migratedUrl = root.path("data").path("image").path("url").asText(null);
            if (migratedUrl == null || migratedUrl.isBlank()) {
                throw new RuntimeException("Missing image url in response");
            }
            return migratedUrl;
        } catch (Exception e) {
            log.error("[LazadaImageService] Failed to migrate image: {}", imageUrl, e);
            throw new RuntimeException("Failed to migrate image to Lazada CDN: " + imageUrl
                    + ". Error: " + e.getMessage(), e);
        }
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
