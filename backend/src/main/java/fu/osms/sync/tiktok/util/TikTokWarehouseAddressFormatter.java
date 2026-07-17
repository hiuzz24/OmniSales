package fu.osms.sync.tiktok.util;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class TikTokWarehouseAddressFormatter {

    private static final String[] ADDRESS_FIELDS = {
            "full_address",
            "address_detail",
            "address_line1",
            "address_line2",
            "address_line3",
            "address_line4",
            "town",
            "post_town",
            "district",
            "distict",
            "city",
            "state",
            "province",
            "region",
            "postal_code"
    };

    private TikTokWarehouseAddressFormatter() {
    }

    public static String format(Map<String, Object> address, String warehouseId) {
        Map<String, String> parts = new LinkedHashMap<>();
        for (String field : ADDRESS_FIELDS) {
            String value = text(address.get(field));
            if (value != null) {
                String normalizedValue = normalize(value);
                boolean alreadyIncluded = parts.keySet().stream()
                        .anyMatch(existing -> existing.equals(normalizedValue) || existing.contains(normalizedValue));
                if (!alreadyIncluded) {
                    parts.put(normalizedValue, value);
                }
            }
        }
        return parts.isEmpty() ? "TikTok warehouse " + warehouseId : String.join(", ", parts.values());
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() || "null".equalsIgnoreCase(text) || "-".equals(text) ? null : text;
    }
}
