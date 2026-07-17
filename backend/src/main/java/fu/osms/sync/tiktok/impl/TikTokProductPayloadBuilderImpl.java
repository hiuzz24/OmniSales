package fu.osms.sync.tiktok.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.catalog.dto.response.PlatformAttributeOptionResponse;
import fu.osms.catalog.dto.response.PlatformAttributeResponse;
import fu.osms.catalog.entity.Product;
import fu.osms.catalog.entity.ProductVariant;
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

    @Override
    public Map<String, Object> buildPayload(TikTokProductPayloadContext context) {
        Product product = context.product();
        Map<String, Object> config = context.config();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", product.getName());
        payload.put("description", product.getDescription());
        payload.put("category_id", text(config.get("categoryId")));
        payload.put("category_version", defaultMessage(text(config.get("categoryVersion")), "v1"));
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
            if (!context.create()) {
                sku.put("id", context.externalVariantIdByVariantId().get(variant.getId()));
            }
            sku.put("seller_sku", variant.getSku());
            sku.put("external_sku_id", variant.getId().toString());
            sku.put("price", Map.of(
                    "amount", variant.getPrice().toPlainString(),
                    "currency", context.currency()
            ));

            List<Map<String, Object>> salesAttributes = salesAttributes(variant, config, context.attributeSchema());
            if (!salesAttributes.isEmpty()) {
                sku.put("sales_attributes", salesAttributes);
            }
            if (context.create()) {
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
                                                       Map<String, Object> config,
                                                       List<PlatformAttributeResponse> schema) {
        Map<String, String> bindings = stringMap(config.get("variantAttributeBindings"));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, String> binding : bindings.entrySet()) {
            Object localValue = variant.getOptionValues() == null ? null : variant.getOptionValues().get(binding.getValue());
            if (localValue == null || localValue.toString().isBlank()) {
                continue;
            }
            PlatformAttributeResponse attribute = schema.stream()
                    .filter(item -> binding.getKey().equals(item.getId()) || binding.getKey().equals(item.getName()))
                    .findFirst().orElse(null);
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("id", attribute == null ? binding.getKey() : attribute.getId());
            if (attribute != null) {
                output.put("name", attribute.getName());
            }
            PlatformAttributeOptionResponse option = attribute == null ? null : attribute.getOptions().stream()
                    .filter(item -> localValue.toString().equalsIgnoreCase(item.getName())).findFirst().orElse(null);
            if (option != null && option.getId() != null) {
                output.put("value_id", option.getId());
            }
            output.put("value_name", localValue.toString());
            result.add(output);
        }
        return result;
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

    private Map<String, String> stringMap(Object value) {
        Map<String, String> result = new HashMap<>();
        map(value).forEach((key, entry) -> {
            if (entry != null) {
                result.put(key, String.valueOf(entry));
            }
        });
        return result;
    }

    private String text(Object value) {
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value);
    }

    private String defaultMessage(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
