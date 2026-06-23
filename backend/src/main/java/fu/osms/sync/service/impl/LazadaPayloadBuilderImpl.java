package fu.osms.sync.service.impl;

import fu.osms.catalog.entity.Product;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.sync.service.LazadaPayloadBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class LazadaPayloadBuilderImpl implements LazadaPayloadBuilder {

    @Value("${lazada.default-category-id:10001001}")
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
    public String buildPayload(Product product, List<ProductVariant> variants, List<String> lazadaImageUrls) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n");
        xml.append("<Request>\n");
        xml.append("  <Product>\n");
        xml.append("    <PrimaryCategory>").append(defaultCategoryId).append("</PrimaryCategory>\n");
        
        xml.append("    <Attributes>\n");
        xml.append("      <name><![CDATA[").append(escapeXml(product.getName())).append("]]></name>\n");
        xml.append("      <brand><![CDATA[").append(escapeXml(product.getBrand() != null ? product.getBrand() : "No Brand")).append("]]></brand>\n");
        if (product.getDescription() != null && !product.getDescription().isBlank()) {
            xml.append("      <description><![CDATA[").append(escapeXml(product.getDescription())).append("]]></description>\n");
        }
        
        // Append apparel specific attributes
        xml.append("      <material>Cotton</material>\n");
        xml.append("      <gender>Unisex</gender>\n");
        xml.append("    </Attributes>\n");

        if (lazadaImageUrls != null && !lazadaImageUrls.isEmpty()) {
            xml.append("    <Images>\n");
            xml.append("      <Image>").append(lazadaImageUrls.get(0)).append("</Image>\n");
            xml.append("    </Images>\n");
        }

        xml.append("    <Skus>\n");
        for (ProductVariant variant : variants) {
            // Lấy cân nặng từ Variant -> Product -> Default
            String finalWeight = defaultPackageWeight;
            if (variant.getWeightGrams() != null && variant.getWeightGrams() > 0) {
                finalWeight = String.valueOf(variant.getWeightGrams() / 1000.0); // Chuyển sang KG
            } else if (product.getWeightGrams() != null && product.getWeightGrams() > 0) {
                finalWeight = String.valueOf(product.getWeightGrams() / 1000.0);
            }

            // Lấy kích thước từ Product Attributes -> Default
            String finalLength = defaultPackageLength;
            String finalWidth = defaultPackageWidth;
            String finalHeight = defaultPackageHeight;

            if (product.getAttributes() != null) {
                if (product.getAttributes().containsKey("length")) {
                    finalLength = product.getAttributes().get("length").toString();
                }
                if (product.getAttributes().containsKey("width")) {
                    finalWidth = product.getAttributes().get("width").toString();
                }
                if (product.getAttributes().containsKey("height")) {
                    finalHeight = product.getAttributes().get("height").toString();
                }
            }

            xml.append("      <Sku>\n");
            xml.append("        <SellerSku>").append(escapeXml(variant.getSku())).append("</SellerSku>\n");
            xml.append("        <price>").append(variant.getPrice() != null ? variant.getPrice() : "0").append("</price>\n");
            xml.append("        <quantity>").append(variant.getInventoryQuantity() != null ? variant.getInventoryQuantity() : "0").append("</quantity>\n");
            xml.append("        <package_weight>").append(finalWeight).append("</package_weight>\n");
            xml.append("        <package_length>").append(finalLength).append("</package_length>\n");
            xml.append("        <package_width>").append(finalWidth).append("</package_width>\n");
            xml.append("        <package_height>").append(finalHeight).append("</package_height>\n");
            
            if (variant.getOptionValues() != null && !variant.getOptionValues().isEmpty()) {
                // Map options dynamically
                int index = 1;
                for (Map.Entry<String, Object> entry : variant.getOptionValues().entrySet()) {
                    // Lazada might require specific names for options like size or color.
                    // Here we just use a generic format, but usually for apparel it's color_family and size.
                    // We map the first option to color_family and the second to size.
                    String val = escapeXml(entry.getValue().toString());
                    if (index == 1) {
                        xml.append("        <color_family>").append(val).append("</color_family>\n");
                    } else if (index == 2) {
                        xml.append("        <size>").append(val).append("</size>\n");
                    }
                    index++;
                }
            }

            if (lazadaImageUrls != null && !lazadaImageUrls.isEmpty()) {
                xml.append("        <Images>\n");
                for (String url : lazadaImageUrls) {
                    xml.append("          <Image>").append(url).append("</Image>\n");
                }
                xml.append("        </Images>\n");
            }

            xml.append("      </Sku>\n");
        }
        xml.append("    </Skus>\n");
        xml.append("  </Product>\n");
        xml.append("</Request>\n");

        return xml.toString();
    }

    private String escapeXml(String input) {
        if (input == null) return "";
        return input; // Inside CDATA, escaping is not strictly necessary unless it contains ]]>
    }
}
