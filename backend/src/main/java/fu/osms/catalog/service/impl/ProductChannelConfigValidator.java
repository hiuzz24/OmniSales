package fu.osms.catalog.service.impl;

import java.net.URI;
import java.util.List;

class ProductChannelConfigValidator {

    boolean isEmpty(Object value) {
        if (value == null) return true;
        if (value instanceof String text) return text.isBlank();
        if (value instanceof List<?> list) return list.isEmpty();
        return false;
    }

    boolean isHttpUrl(String value) {
        try {
            URI uri = URI.create(value.trim());
            return uri.isAbsolute() && ("http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme()));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    boolean isPositiveNumber(Object value) {
        if (value == null || value.toString().isBlank()) return false;
        try {
            return new java.math.BigDecimal(value.toString()).signum() > 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }
}
