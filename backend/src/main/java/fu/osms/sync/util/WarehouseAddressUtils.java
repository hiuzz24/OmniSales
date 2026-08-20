package fu.osms.sync.util;

import fu.osms.inventory.addressmatching.AddressMatcher;
import fu.osms.inventory.addressmatching.AddressMatchResult;
import fu.osms.inventory.addressmatching.MatchLevel;

import java.util.Locale;
import java.util.Map;

/**
 * Shared utilities for warehouse address normalization and comparison.
 * Delegates actual comparison to {@link AddressMatcher}.
 */
public final class WarehouseAddressUtils {

    private WarehouseAddressUtils() {
    }

    private static final Map<String, String> POSTAL_CODE_TO_CITY = Map.of(
            "10000", "Hà Nội", "100000", "Hà Nội",
            "70000", "Hồ Chí Minh", "700000", "Hồ Chí Minh",
            "20000", "Hải Phòng", "200000", "Hải Phòng",
            "50000", "Đà Nẵng", "500000", "Đà Nẵng"
    );

    private static final MatchLevel MIN_SIMILAR_LEVEL = MatchLevel.MEDIUM;

    /**
     * Replace known Vietnamese postal codes with city names and strip remaining
     * numeric-only tokens (4-6 digits). This prevents postal codes from
     * distorting address comparison scores.
     */
    public static String stripPostalCodes(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String resolved = value;
        for (Map.Entry<String, String> entry : POSTAL_CODE_TO_CITY.entrySet()) {
            if (resolved.contains(entry.getKey())) {
                String city = entry.getValue();
                String lower = resolved.toLowerCase(Locale.ROOT);
                String cityLower = city.toLowerCase(Locale.ROOT);
                if (!lower.contains(cityLower)) {
                    resolved = resolved.replace(entry.getKey(), city);
                } else {
                    resolved = resolved.replace(entry.getKey(), "");
                }
            }
        }
        return resolved.replaceAll("\\b\\d{4,6}\\b", "")
                .replaceAll(",\\s*,", ",").replaceAll("^\\s*,|,\\s*$", "").trim();
    }

    /**
     * Compare two addresses using the AddressMatching module.
     * Pre-processes with {@link #stripPostalCodes(String)} to normalize postal codes.
     *
     * @return true if addresses match at MEDIUM level or above
     */
    public static boolean isAddressSimilar(String address1, String address2) {
        if (address1 == null && address2 == null) return true;
        if (address1 == null || address2 == null) return false;
        String cleaned1 = stripPostalCodes(address1);
        String cleaned2 = stripPostalCodes(address2);
        AddressMatchResult result = AddressMatcher.compare(cleaned1, cleaned2);
        return result.getLevel().compareTo(MIN_SIMILAR_LEVEL) <= 0;
    }

    /**
     * Full comparison using AddressMatching module.
     * Pre-processes with {@link #stripPostalCodes(String)} to normalize postal codes.
     */
    public static AddressMatchResult compare(String address1, String address2) {
        String cleaned1 = stripPostalCodes(address1);
        String cleaned2 = stripPostalCodes(address2);
        return AddressMatcher.compare(cleaned1, cleaned2);
    }
}
