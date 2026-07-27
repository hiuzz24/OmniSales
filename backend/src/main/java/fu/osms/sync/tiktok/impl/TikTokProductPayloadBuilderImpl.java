package fu.osms.sync.tiktok.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.catalog.dto.response.PlatformAttributeOptionResponse;
import fu.osms.catalog.dto.response.PlatformAttributeResponse;
import fu.osms.catalog.dto.TikTokProductTitleInput;
import fu.osms.catalog.dto.TikTokProductTitleResult;
import fu.osms.catalog.entity.Product;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.catalog.service.TikTokProductTitleResolver;
import fu.osms.sync.tiktok.TikTokProductPayloadBuilder;
import fu.osms.sync.tiktok.dto.TikTokProductPayloadContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TikTokProductPayloadBuilderImpl implements TikTokProductPayloadBuilder {

    private final ObjectMapper objectMapper;
    private final TikTokProductTitleResolver titleResolver;

    @Override
    public Map<String, Object> buildPayload(TikTokProductPayloadContext context) {
        Product product = context.product();
        Map<String, Object> config = context.config();
        Map<String, Object> payload = new LinkedHashMap<>();
        TikTokProductTitleResult titleResult = titleResolver.resolve(new TikTokProductTitleInput(
                text(config.get("listingTitle")),
                product.getName(),
                text(config.get("categoryName")),
                text(config.get("brandName")),
                product.getDescription()
        ));
        if (!titleResult.valid()) throw new IllegalStateException(titleResult.validationError());
        payload.put("title", titleResult.title());
        payload.put("description", product.getDescription());
        payload.put("category_id", text(config.get("categoryId")));
        payload.put("category_version", defaultMessage(text(config.get("categoryVersion")), "v2"));
        payload.put("main_images", context.imageUris().stream().map(uri -> Map.of("uri", uri)).toList());
        if (context.sizeChartImageUri() != null && !context.sizeChartImageUri().isBlank()) {
            payload.put("size_chart", Map.of("image", Map.of("uri", context.sizeChartImageUri())));
        }
        payload.put("package_weight", Map.of("value", kilograms(product), "unit", "KILOGRAM"));

        Map<String, Object> dimensions = dimensions(product);
        if (!dimensions.isEmpty()) {
            payload.put("package_dimensions", dimensions);
        }
        if (text(config.get("brandId")) != null) {
            payload.put("brand_id", text(config.get("brandId")));
        }
        payload.put("product_attributes", productAttributes(config));
        payload.put("external_product_id", product.getId().toString());
        if (context.create()) {
            payload.put("idempotency_key", UUID.randomUUID().toString());
        }

        List<Map<String, Object>> skus = new ArrayList<>();
        for (ProductVariant variant : context.variants()) {
            if (Boolean.FALSE.equals(variant.getIsActive())) {
                continue;
            }
            Map<String, Object> sku = new LinkedHashMap<>();
            String externalVariantId = context.externalVariantIdByVariantId().get(variant.getId());
            if (!context.create() && externalVariantId != null && !externalVariantId.isBlank()) {
                sku.put("id", externalVariantId);
            }
            sku.put("seller_sku", variant.getSku());
            sku.put("external_sku_id", variant.getId().toString());
            sku.put("price", Map.of(
                    "amount", variant.getPrice().toPlainString(),
                    "currency", context.currency()
            ));

            List<Map<String, Object>> salesAttributes = salesAttributes(
                    variant, context.variants(), context.attributeSchema());
            if (activeVariantCount(context.variants()) > 1 && salesAttributes.isEmpty()) {
                throw new IllegalStateException(
                        "Missing TikTok sales attributes for SKU " + variant.getSku());
            }
            if (!salesAttributes.isEmpty()) {
                sku.put("sales_attributes", salesAttributes);
            }
            if (context.create() || externalVariantId == null || externalVariantId.isBlank()) {
                sku.put("inventory", List.of(Map.of(
                        "warehouse_id", context.defaultWarehouseId(),
                        "quantity", 0
                )));
            }
            skus.add(sku);
        }
        payload.put("skus", skus);
        return payload;
    }

    private List<Map<String, Object>> productAttributes(Map<String, Object> config) {
        Map<String, Object> attributes = map(config.get("attributes"));
        List<Map<String, Object>> result = new ArrayList<>();
        attributes.forEach((key, value) -> {
            Map<String, Object> item = map(value);
            String attributeId = defaultMessage(text(item.get("attributeId")), key);
            String valueId = text(item.get("valueId"));
            String valueName = defaultMessage(text(item.get("valueName")), value instanceof String ? (String) value : null);
            if (attributeId == null || (valueId == null && valueName == null)) {
                return;
            }
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("id", attributeId);
            Map<String, Object> selectedValue = new LinkedHashMap<>();
            if (valueId != null) {
                selectedValue.put("id", valueId);
            }
            if (valueName != null) {
                selectedValue.put("name", valueName);
            }
            output.put("values", List.of(selectedValue));
            result.add(output);
        });
        return result;
    }

    private List<Map<String, Object>> salesAttributes(ProductVariant variant,
                                                       List<ProductVariant> variants,
                                                       List<PlatformAttributeResponse> schema) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (PlatformAttributeResponse attribute : schema) {
            if (!Boolean.TRUE.equals(attribute.getSaleProperty())) continue;
            String attributeId = text(attribute.getId());
            String attributeName = text(attribute.getName());
            if (attributeId == null && attributeName == null) {
                continue;
            }

            String optionKey = tikTokVariantOptionKey(attribute);
            if (optionKey == null) continue;
            boolean attributeIsUsed = variants.stream()
                    .filter(item -> !Boolean.FALSE.equals(item.getIsActive()))
                    .map(item -> item.getOptionValues() == null ? null : item.getOptionValues().get(optionKey))
                    .anyMatch(value -> text(value) != null);
            if (!attributeIsUsed) continue;

            String selectedValue = text(variant.getOptionValues() == null
                    ? null : variant.getOptionValues().get(optionKey));
            if (selectedValue == null) {
                throw new IllegalStateException("Missing TikTok "
                        + defaultMessage(attributeName, attributeId) + " value for SKU " + variant.getSku());
            }

            PlatformAttributeOptionResponse option = attribute.getOptions() == null ? null : attribute.getOptions().stream()
                    .filter(item -> selectedValue.equals(item.getId()) || selectedValue.equalsIgnoreCase(item.getName()))
                    .findFirst().orElse(null);
            if (attribute.getOptions() != null && !attribute.getOptions().isEmpty() && option == null) {
                throw new IllegalStateException("TikTok sales attribute value is no longer valid for "
                        + defaultMessage(attributeName, attributeId) + ", SKU " + variant.getSku()
                        + ". Save the platform configuration again.");
            }

            Map<String, Object> output = new LinkedHashMap<>();
            if (attributeId != null) output.put("id", attributeId);
            if (attributeName != null) output.put("name", attributeName);
            if (option != null && text(option.getId()) != null) {
                output.put("value_id", option.getId());
            }
            String valueName = option == null ? selectedValue : defaultMessage(option.getName(), selectedValue);
            output.put("value_name", valueName);
            result.add(output);
        }
        return result;
    }

    private String tikTokVariantOptionKey(PlatformAttributeResponse attribute) {
        if ("100000".equals(attribute.getId())) return "Màu";
        if ("100007".equals(attribute.getId())) return "Size";
        String name = text(attribute.getName());
        if (name == null) return null;
        String normalized = name.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("màu") || normalized.contains("color")) return "Màu";
        if (normalized.contains("kích cỡ") || normalized.equals("size")) return "Size";
        return null;
    }

    private long activeVariantCount(List<ProductVariant> variants) {
        return variants.stream().filter(variant -> !Boolean.FALSE.equals(variant.getIsActive())).count();
    }

    private Map<String, Object> dimensions(Product product) {
        Map<String, Object> attributes = product.getAttributes() == null ? Map.of() : product.getAttributes();
        String length = text(attributes.get("packageLengthCm"));
        String width = text(attributes.get("packageWidthCm"));
        String height = text(attributes.get("packageHeightCm"));
        if (length == null || width == null || height == null) {
            return Map.of();
        }
        return Map.of("length", length, "width", width, "height", height, "unit", "CENTIMETER");
    }

    private String kilograms(Product product) {
        return BigDecimal.valueOf(product.getWeightGrams()).movePointLeft(3).stripTrailingZeros().toPlainString();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> map ? objectMapper.convertValue(map, new TypeReference<>() {}) : new HashMap<>();
    }

    private String text(Object value) {
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value);
    }

    private String defaultMessage(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
