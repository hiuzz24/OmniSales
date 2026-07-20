package fu.osms.sync.lazada.service.impl;

import fu.osms.catalog.entity.Product;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.sync.lazada.service.LazadaPayloadBuilder;
import fu.osms.sync.lazada.dto.LazadaMigratedImages;
import fu.osms.sync.lazada.dto.LazadaProductConfig;
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
import java.util.List;
import java.util.Map;

@Service
public class LazadaPayloadBuilderImpl implements LazadaPayloadBuilder {

    private static final int MAX_PRODUCT_IMAGES = 3;
    private static final int MAX_DESCRIPTION_LENGTH = 25_000;

    @Override
    public String buildPayload(Product product,
                               List<ProductVariant> variants,
                              LazadaMigratedImages migratedImages,
                              Map<String, String> externalSkuIdBySku,
                              LazadaProductConfig config,
                              boolean isCreate) {
        try {
            Document document = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .newDocument();

            Element request = document.createElement("Request");
            document.appendChild(request);

            Element productElement = appendElement(document, request, "Product");
            if (isCreate) {
                appendTextElement(document, productElement, "PrimaryCategory", config.getCategoryId());
            }

            List<String> productImageUrls = migratedImages == null
                    ? List.of()
                    : migratedImages.productImageUrls().stream().limit(MAX_PRODUCT_IMAGES).toList();
            Element attributes = appendElement(document, productElement, "Attributes");
            appendCdataElement(document, attributes, "name", product.getName());
            appendTextElement(document, attributes, "brand_id", config.getBrandId());
            appendCdataElement(document, attributes, "description", buildDescription(product, productImageUrls));
            appendConfiguredAttributes(document, attributes, config);

            if (!productImageUrls.isEmpty()) {
                Element productImages = appendElement(document, productElement, "Images");
                for (String imageUrl : productImageUrls) {
                    appendTextElement(document, productImages, "Image", imageUrl);
                }
            }

            Element skus = appendElement(document, productElement, "Skus");
            for (ProductVariant variant : variants) {
                Element sku = appendElement(document, skus, "Sku");
                String skuId = externalSkuIdBySku == null ? null : externalSkuIdBySku.get(variant.getSku());
                if (skuId != null && !skuId.isBlank()) {
                    appendTextElement(document, sku, "SkuId", skuId);
                }
                appendTextElement(document, sku, "SellerSku", variant.getSku());
                appendTextElement(document, sku, "price", resolveVariantPrice(variant));
                if (isCreate) {
                    appendTextElement(document, sku, "quantity", "0");
                }
                appendTextElement(document, sku, "package_weight", resolveWeight(product));
                appendTextElement(document, sku, "package_length", requiredAttribute(product, "packageLengthCm"));
                appendTextElement(document, sku, "package_width", requiredAttribute(product, "packageWidthCm"));
                appendTextElement(document, sku, "package_height", requiredAttribute(product, "packageHeightCm"));

                appendVariantAttributes(document, sku, variant, config);
                List<String> skuImageUrls = migratedImages == null || variant.getId() == null
                        ? List.of()
                        : migratedImages.variantImageUrls().getOrDefault(variant.getId(), List.of());
                appendSkuImages(document, sku, skuImageUrls);
            }

            return toXml(document);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build Lazada product payload", e);
        }
    }

    private Element appendElement(Document document, Element parent, String name) {
        Element element = document.createElement(name);
        parent.appendChild(element);
        return element;
    }

    private void appendTextElement(Document document, Element parent, String name, String value) {
        Element element = appendElement(document, parent, name);
        element.setTextContent(value != null ? value : "");
    }

    private void appendCdataElement(Document document, Element parent, String name, String value) {
        Element element = appendElement(document, parent, name);
        element.appendChild(document.createCDATASection(value != null ? value : ""));
    }

    private String resolveWeight(Product product) {
        if (product.getWeightGrams() != null && product.getWeightGrams() > 0) {
            return String.valueOf(product.getWeightGrams() / 1000.0);
        }
        throw new IllegalStateException("Missing package weight");
    }

    private String requiredAttribute(Product product, String key) {
        if (product.getAttributes() != null && product.getAttributes().containsKey(key)) {
            Object value = product.getAttributes().get(key);
            if (value != null && !value.toString().isBlank()) return value.toString();
        }
        throw new IllegalStateException("Missing " + key);
    }

    private String resolveVariantPrice(ProductVariant variant) {
        if (variant.getPrice() == null || variant.getPrice().signum() <= 0) {
            throw new IllegalStateException("Missing selling price for SKU " + variant.getSku());
        }
        return variant.getPrice().toPlainString();
    }

    private void appendConfiguredAttributes(Document document, Element attributes, LazadaProductConfig config) {
        if (config.getAttributes() == null) return;
        config.getAttributes().forEach((name, value) -> {
            if (value == null || value.toString().isBlank()) return;
            if ("name".equals(name) || "brand".equals(name) || "brand_id".equals(name) || "description".equals(name)) return;
            appendTextElement(document, attributes, name, value.toString());
        });
    }

    private void appendVariantAttributes(Document document,
                                         Element sku,
                                         ProductVariant variant,
                                         LazadaProductConfig config) {
        if (config.getVariantAttributeValueMappings() == null) return;
        config.getVariantAttributeValueMappings().forEach((platformAttribute, skuMappings) -> {
            String platformValue = skuMappings == null ? null : skuMappings.get(variant.getSku());
            if (platformValue == null || platformValue.isBlank()) {
                throw new IllegalStateException("Missing Lazada " + platformAttribute
                        + " value for SKU " + variant.getSku());
            }
            appendTextElement(document, sku, platformAttribute, platformValue);
        });
    }

    private void appendSkuImages(Document document, Element sku, List<String> lazadaImageUrls) {
        if (lazadaImageUrls == null || lazadaImageUrls.isEmpty()) {
            return;
        }

        Element images = appendElement(document, sku, "Images");
        for (String url : lazadaImageUrls) {
            appendTextElement(document, images, "Image", url);
        }
    }

    private String buildDescription(Product product, List<String> imageUrls) {
        String description = product.getDescription() == null ? "" : product.getDescription().trim();

        StringBuilder result = new StringBuilder(description);
        if (!description.isBlank()) {
            result.append('\n');
        }
        result.append("<div class=\"osms-product-images\">");
        String alt = escapeHtmlAttribute(product.getName());
        for (String imageUrl : imageUrls) {
            result.append("<img src=\"")
                    .append(escapeHtmlAttribute(imageUrl))
                    .append("\" alt=\"")
                    .append(alt)
                    .append("\" />");
        }
        result.append("</div>");

        if (result.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalStateException("Lazada description exceeds 25000 characters after adding product images");
        }
        return result.toString();
    }

    private String escapeHtmlAttribute(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private String toXml(Document document) throws Exception {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "no");

        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(writer));
        return writer.toString();
    }
}
