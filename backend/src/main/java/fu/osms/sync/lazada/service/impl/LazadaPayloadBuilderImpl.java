package fu.osms.sync.lazada.service.impl;

import fu.osms.catalog.entity.Product;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.sync.lazada.service.LazadaPayloadBuilder;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${lazada.default-category-id:7932}")
    private String defaultCategoryId;

    @Value("${lazada.default-package-weight:0.5}")
    private String defaultPackageWeight;

    @Value("${lazada.default-package-length:20}")
    private String defaultPackageLength;

    @Value("${lazada.default-package-width:15}")
    private String defaultPackageWidth;

    @Value("${lazada.default-package-height:5}")
    private String defaultPackageHeight;

    @Override
    public String buildPayload(Product product, List<ProductVariant> variants, List<String> lazadaImageUrls, Map<String, String> externalSkuIdBySku) {
        try {
            Document document = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .newDocument();

            Element request = document.createElement("Request");
            document.appendChild(request);

            Element productElement = appendElement(document, request, "Product");
            appendTextElement(document, productElement, "PrimaryCategory", defaultCategoryId);

            Element attributes = appendElement(document, productElement, "Attributes");
            appendCdataElement(document, attributes, "name", product.getName());
            appendCdataElement(document, attributes, "brand", product.getBrand() != null ? product.getBrand() : "No Brand");
            if (product.getDescription() != null && !product.getDescription().isBlank()) {
                appendCdataElement(document, attributes, "description", product.getDescription());
            }
            appendTextElement(document, attributes, "clothing_material", "100% Cotton");
            appendTextElement(document, attributes, "fa_pattern", "Plain");
            appendTextElement(document, attributes, "size_chart", "https://sg-test-11.slatic.net/p/53cfc24074576554c748b6fa43eb833e.png");
            appendTextElement(document, attributes, "gender", "Unisex");

            if (lazadaImageUrls != null && !lazadaImageUrls.isEmpty()) {
                Element productImages = appendElement(document, productElement, "Images");
                appendTextElement(document, productImages, "Image", lazadaImageUrls.get(0));
            }

            Element skus = appendElement(document, productElement, "Skus");
            for (ProductVariant variant : variants) {
                Element sku = appendElement(document, skus, "Sku");
                String skuId = externalSkuIdBySku != null ? externalSkuIdBySku.get(variant.getSku()) : null;
                if (skuId != null && !skuId.isBlank()) {
                    appendTextElement(document, sku, "SkuId", skuId);
                }
                appendTextElement(document, sku, "SellerSku", variant.getSku());
                appendTextElement(document, sku, "price", variant.getPrice() != null ? variant.getPrice().toPlainString() : "0");
                appendTextElement(document, sku, "quantity", "0");
                appendTextElement(document, sku, "package_weight", resolveWeight(product, variant));
                appendTextElement(document, sku, "package_length", resolveAttribute(product, "length", defaultPackageLength));
                appendTextElement(document, sku, "package_width", resolveAttribute(product, "width", defaultPackageWidth));
                appendTextElement(document, sku, "package_height", resolveAttribute(product, "height", defaultPackageHeight));

                appendVariantOptions(document, sku, variant);
                appendSkuImages(document, sku, lazadaImageUrls);
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

    private String resolveWeight(Product product, ProductVariant variant) {
        if (variant.getWeightGrams() != null && variant.getWeightGrams() > 0) {
            return String.valueOf(variant.getWeightGrams() / 1000.0);
        }
        if (product.getWeightGrams() != null && product.getWeightGrams() > 0) {
            return String.valueOf(product.getWeightGrams() / 1000.0);
        }
        return defaultPackageWeight;
    }

    private String resolveAttribute(Product product, String key, String defaultValue) {
        if (product.getAttributes() != null && product.getAttributes().containsKey(key)) {
            Object value = product.getAttributes().get(key);
            return value != null ? value.toString() : defaultValue;
        }
        return defaultValue;
    }

    private void appendVariantOptions(Document document, Element sku, ProductVariant variant) {
        if (variant.getOptionValues() == null || variant.getOptionValues().isEmpty()) {
            return;
        }

        int index = 1;
        for (Map.Entry<String, Object> entry : variant.getOptionValues().entrySet()) {
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            if (index == 1) {
                appendTextElement(document, sku, "color_family", value);
            } else if (index == 2) {
                appendTextElement(document, sku, "size", value);
            }
            index++;
        }
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

    private String toXml(Document document) throws Exception {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "no");

        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(writer));
        return writer.toString();
    }
}
