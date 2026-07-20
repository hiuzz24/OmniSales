package fu.osms.sync.lazada.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.catalog.entity.ProductImage;
import fu.osms.sync.lazada.dto.LazadaMigratedImages;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LazadaImageServiceImpl implements LazadaImageService {

    private static final int MAX_PRODUCT_IMAGES = 3;
    private static final int MAX_SKU_IMAGES = 8;

    private final LazadaAuthorizedApiClient lazadaApiClient;
    private final ObjectMapper objectMapper;

    @Override
    public LazadaMigratedImages migrateImages(List<ProductImage> images, UUID channelId) {
        if (images == null || images.isEmpty()) {
            throw new IllegalStateException("Missing Lazada main product image");
        }

        List<ProductImage> orderedImages = images.stream()
                .sorted(Comparator
                        .comparing((ProductImage image) -> !Boolean.TRUE.equals(image.getIsPrimary()))
                        .thenComparing(image -> image.getSortOrder() == null ? Short.MAX_VALUE : image.getSortOrder()))
                .toList();

        Set<String> productSources = new LinkedHashSet<>();
        Map<UUID, Set<String>> variantSources = new LinkedHashMap<>();
        for (ProductImage image : orderedImages) {
            String originalUrl = image.getUrl();
            if (originalUrl == null || originalUrl.isBlank()) {
                continue;
            }
            if (image.getVariant() == null) {
                productSources.add(originalUrl.trim());
                continue;
            }

            UUID variantId = image.getVariant().getId();
            if (variantId != null) {
                variantSources.computeIfAbsent(variantId, ignored -> new LinkedHashSet<>())
                        .add(originalUrl.trim());
            }
        }

        List<String> selectedProductSources = productSources.stream().limit(MAX_PRODUCT_IMAGES).toList();
        if (selectedProductSources.isEmpty()) {
            throw new IllegalStateException("Missing Lazada main product image");
        }

        Map<UUID, List<String>> selectedVariantSources = new LinkedHashMap<>();
        variantSources.forEach((variantId, urls) ->
                selectedVariantSources.put(variantId, urls.stream().limit(MAX_SKU_IMAGES).toList()));

        Set<String> selectedSources = new LinkedHashSet<>(selectedProductSources);
        selectedVariantSources.values().forEach(selectedSources::addAll);

        Map<String, String> migratedBySource = new HashMap<>();
        for (String sourceUrl : selectedSources) {
            migratedBySource.put(sourceUrl, migrateImageUrl(sourceUrl, channelId));
        }

        List<String> productImageUrls = selectedProductSources.stream()
                .map(migratedBySource::get)
                .toList();
        Map<UUID, List<String>> variantImageUrls = new LinkedHashMap<>();
        selectedVariantSources.forEach((variantId, urls) -> variantImageUrls.put(
                variantId,
                urls.stream().map(migratedBySource::get).toList()
        ));

        return new LazadaMigratedImages(productImageUrls, variantImageUrls);
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
