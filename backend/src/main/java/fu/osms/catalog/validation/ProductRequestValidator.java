package fu.osms.catalog.validation;

import fu.osms.catalog.dto.request.ProductRequest;
import fu.osms.catalog.dto.request.ProductVariantRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class ProductRequestValidator implements ConstraintValidator<ValidProductRequest, ProductRequest> {

    private static final List<String> DIMENSION_FIELDS = List.of(
            "packageWidthCm",
            "packageHeightCm",
            "packageLengthCm"
    );

    @Override
    public boolean isValid(ProductRequest request, ConstraintValidatorContext context) {
        if (request == null) {
            return true;
        }

        boolean valid = true;
        context.disableDefaultConstraintViolation();

        for (String field : DIMENSION_FIELDS) {
            if (!isPositive(request.getAttributes() == null ? null : request.getAttributes().get(field))) {
                addViolation(context, "attributes." + field, dimensionMessage(field));
                valid = false;
            }
        }

        if (request.getVariants() == null) {
            return valid;
        }

        for (int index = 0; index < request.getVariants().size(); index++) {
            ProductVariantRequest variant = request.getVariants().get(index);
            if (variant == null || Boolean.FALSE.equals(variant.getIsActive())) {
                continue;
            }

            if (!hasText(variant.getSku())) {
                addVariantViolation(context, index, "sku", "SKU không được để trống");
                valid = false;
            } else if (variant.getSku().trim().length() > 100) {
                addVariantViolation(context, index, "sku", "SKU tối đa 100 ký tự");
                valid = false;
            }
            if (!hasText(variant.getBarcode())) {
                addVariantViolation(context, index, "barcode", "Barcode không được để trống");
                valid = false;
            } else if (variant.getBarcode().trim().length() > 100) {
                addVariantViolation(context, index, "barcode", "Barcode tối đa 100 ký tự");
                valid = false;
            }
            if (variant.getPrice() == null || variant.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                addVariantViolation(context, index, "price", "Giá bán phải lớn hơn 0");
                valid = false;
            }
            if (variant.getCostPrice() == null || variant.getCostPrice().compareTo(BigDecimal.ZERO) < 0) {
                addVariantViolation(context, index, "costPrice", "Giá vốn phải là số không âm");
                valid = false;
            }

            Map<String, Object> options = variant.getOptionValues();
            if (!hasText(options == null ? null : options.get("Size"))) {
                addVariantOptionViolation(context, index, "Size", "Size không được để trống");
                valid = false;
            }
            if (!hasText(options == null ? null : options.get("Màu"))) {
                addVariantOptionViolation(context, index, "Màu", "Màu sắc không được để trống");
                valid = false;
            }
            if (Boolean.TRUE.equals(request.getHasVariants())
                    && (variant.getImages() == null || variant.getImages().isEmpty())) {
                addVariantViolation(context, index, "images", "Vui lòng thêm ảnh cho biến thể");
                valid = false;
            }
        }

        return valid;
    }

    private boolean isPositive(Object value) {
        if (value == null) {
            return false;
        }
        try {
            return new BigDecimal(String.valueOf(value)).compareTo(BigDecimal.ZERO) > 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private boolean hasText(Object value) {
        return value != null && !String.valueOf(value).isBlank();
    }

    private String dimensionMessage(String field) {
        return switch (field) {
            case "packageWidthCm" -> "Chiều rộng đóng gói phải lớn hơn 0";
            case "packageHeightCm" -> "Chiều cao đóng gói phải lớn hơn 0";
            default -> "Chiều dài đóng gói phải lớn hơn 0";
        };
    }

    private void addViolation(ConstraintValidatorContext context, String property, String message) {
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(property)
                .addConstraintViolation();
    }

    private void addVariantViolation(ConstraintValidatorContext context, int index, String property, String message) {
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode("variants")
                .inIterable().atIndex(index)
                .addPropertyNode(property)
                .addConstraintViolation();
    }

    private void addVariantOptionViolation(ConstraintValidatorContext context, int index, String option, String message) {
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode("variants")
                .inIterable().atIndex(index)
                .addPropertyNode("optionValues")
                .addPropertyNode(option)
                .addConstraintViolation();
    }
}
