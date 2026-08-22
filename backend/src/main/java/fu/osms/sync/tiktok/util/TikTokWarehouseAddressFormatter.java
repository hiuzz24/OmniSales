package fu.osms.sync.tiktok.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
            "region"
    };

    private TikTokWarehouseAddressFormatter() {
    }

    public static String format(Map<String, Object> address, String warehouseId) {
        if (address == null || address.isEmpty()) {
            return "TikTok warehouse " + warehouseId;
        }
        List<String> parts = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String field : ADDRESS_FIELDS) {
            String value = text(address.get(field));
            if (value != null) {
                String normalized = normalize(value);
                if (seen.add(normalized)) {
                    parts.add(value);
                }
            }
        }
        return parts.isEmpty() ? "TikTok warehouse " + warehouseId : String.join(", ", parts);
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() || "null".equalsIgnoreCase(text) || "-".equals(text) ? null : text;
    }
}
