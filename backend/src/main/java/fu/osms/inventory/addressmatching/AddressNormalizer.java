package fu.osms.inventory.addressmatching;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AddressNormalizer {

    private AddressNormalizer() {
    }

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private static final Pattern HOUSE_NUMBER_PATTERN =
            Pattern.compile("^(?:so\\s*)?(\\d+(?:[/\\\\\\-]\\d+)?(?:\\s*[a-zA-Z])?(?=[\\s,/\\\\\\-]|$))");

    private static final List<String> STREET_PREFIXES = List.of(
            "duong", "pho", "street", "road", "d.", "p.", "st", "rd"
    );

    private static final List<String> WARD_INDICATORS = List.of(
            "phuong", "ward", "p."
    );

    private static final List<String> DISTRICT_INDICATORS = List.of(
            "quan", "huyen", "district", "dist", "q.", "h."
    );

    private static final List<String> CITY_INDICATORS = List.of(
            "thanh pho", "city", "tp."
    );

    private static final List<String[]> COUNTRY_REPLACEMENTS = List.of(
            new String[]{"the socialist republic of viet nam", "vietnam"},
            new String[]{"the socialist republic of vietnam", "vietnam"},
            new String[]{"socialist republic of viet nam", "vietnam"},
            new String[]{"socialist republic of vietnam", "vietnam"},
            new String[]{"viet nam", "vietnam"}
    );

    public static String normalize(String address) {
        if (address == null || address.isBlank()) return "";
        String result = address.toLowerCase().trim();
        result = stripDiacritics(result);
        result = normalizeWhitespace(result);
        result = normalizeCountryAliases(result);
        return result;
    }

    public static AddressComponents extractComponents(String rawAddress) {
        if (rawAddress == null || rawAddress.isBlank()) {
            return AddressComponents.builder().rawAddress("").build();
        }

        String normalized = normalize(rawAddress);
        String houseNumber = extractHouseNumber(normalized);
        String street = extractStreet(normalized, houseNumber);
        String remainingAfterStreet = getRemainingAfterStreet(normalized, houseNumber, street);
        Map<String, String> locations = extractLocationComponents(remainingAfterStreet);

        return AddressComponents.builder()
                .rawAddress(rawAddress)
                .houseNumber(houseNumber)
                .street(street)
                .ward(locations.get("ward"))
                .district(locations.get("district"))
                .city(locations.get("city"))
                .country(locations.get("country"))
                .build();
    }

    public static String stripDiacritics(String input) {
        if (input == null) return null;
        String result = input;
        result = result.replace('\u0111', 'd');
        result = result.replace('\u0110', 'D');
        return Normalizer.normalize(result, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    private static String extractHouseNumber(String normalized) {
        Matcher m = HOUSE_NUMBER_PATTERN.matcher(normalized);
        if (m.find()) {
            return m.group(1).replaceAll("\\s+", "").toLowerCase();
        }
        return null;
    }

    private static int getHouseNumberEndIndex(String normalized) {
        Matcher m = HOUSE_NUMBER_PATTERN.matcher(normalized);
        if (m.find()) {
            return m.end();
        }
        return 0;
    }

    private static String extractStreet(String normalized, String houseNumber) {
        int startIdx = getHouseNumberEndIndex(normalized);
        String remaining = normalized.substring(startIdx);
        remaining = remaining.replaceAll("^[,;\\s]+", "");

        for (String prefix : STREET_PREFIXES) {
            if (remaining.startsWith(prefix + " ") || remaining.startsWith(prefix + ".")) {
                remaining = remaining.substring(prefix.length());
                if (remaining.startsWith(".")) {
                    remaining = remaining.substring(1);
                }
                remaining = remaining.trim();
                break;
            }
        }

        int commaIdx = remaining.indexOf(',');
        if (commaIdx > 0) {
            remaining = remaining.substring(0, commaIdx);
        }

        return remaining.trim();
    }

    private static String getRemainingAfterStreet(String normalized, String houseNumber, String street) {
        int startIdx = getHouseNumberEndIndex(normalized);
        String result = normalized.substring(startIdx);
        result = result.replaceAll("^[,;\\s]+", "");

        for (String prefix : STREET_PREFIXES) {
            if (result.startsWith(prefix + " ") || result.startsWith(prefix + ".")) {
                result = result.substring(prefix.length());
                if (result.startsWith(".")) {
                    result = result.substring(1);
                }
                result = result.trim();
                break;
            }
        }

        if (street != null && !street.isEmpty()) {
            int streetIdx = result.toLowerCase().indexOf(street.toLowerCase());
            if (streetIdx >= 0) {
                result = result.substring(streetIdx + street.length());
            }
        }

        result = result.replaceAll("^[,;\\s]+", "");
        return result;
    }

    private static Map<String, String> extractLocationComponents(String remaining) {
        Map<String, String> locations = new LinkedHashMap<>();
        if (remaining == null || remaining.isBlank()) return locations;

        String[] parts = remaining.split(",");
        boolean hasCityOrCountry = false;

        for (String part : parts) {
            part = part.trim().toLowerCase();
            if (part.isEmpty()) continue;

            String country = matchCountry(part);
            if (country != null) {
                locations.putIfAbsent("country", country);
                hasCityOrCountry = true;
                continue;
            }

            String ward = matchAndStripIndicator(part, WARD_INDICATORS);
            if (ward != null) {
                locations.putIfAbsent("ward", ward);
                continue;
            }

            String district = matchAndStripIndicator(part, DISTRICT_INDICATORS);
            if (district != null) {
                locations.putIfAbsent("district", district);
                continue;
            }

            String city = matchAndStripIndicator(part, CITY_INDICATORS);
            if (city != null) {
                locations.putIfAbsent("city", city);
                hasCityOrCountry = true;
                continue;
            }

            if (!hasCityOrCountry && !locations.containsKey("city")) {
                locations.putIfAbsent("city", part.trim());
                hasCityOrCountry = true;
            }
        }

        return locations;
    }

    private static String matchCountry(String part) {
        String trimmed = part.trim();
        if (trimmed.equals("vietnam")) return "vietnam";
        for (String[] replacement : COUNTRY_REPLACEMENTS) {
            if (trimmed.equals(replacement[0]) || trimmed.contains(replacement[0])) {
                return replacement[1];
            }
        }
        return null;
    }

    private static String matchAndStripIndicator(String part, List<String> indicators) {
        for (String indicator : indicators) {
            if (part.startsWith(indicator + " ")) {
                return part.substring(indicator.length() + 1).trim();
            }
            if (part.endsWith(" " + indicator)) {
                return part.substring(0, part.length() - indicator.length() - 1).trim();
            }
            if (part.equals(indicator) || part.equals(indicator + ".")) {
                return "";
            }
        }
        return null;
    }

    private static String normalizeWhitespace(String input) {
        return WHITESPACE.matcher(input).replaceAll(" ").trim();
    }

    private static String normalizeCountryAliases(String input) {
        String result = input;
        for (String[] replacement : COUNTRY_REPLACEMENTS) {
            result = result.replace(replacement[0], replacement[1]);
        }
        return result;
    }
}
