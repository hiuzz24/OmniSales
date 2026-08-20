package fu.osms.inventory.addressmatching;

import java.util.*;

public final class AddressMatcher {

    private AddressMatcher() {
    }

    private static final double WEIGHT_HOUSE = 0.40;
    private static final double WEIGHT_STREET = 0.40;
    private static final double WEIGHT_LOCATION = 0.20;

    private static final double THRESHOLD_EXACT = 0.95;
    private static final double THRESHOLD_HIGH = 0.85;
    private static final double THRESHOLD_MEDIUM = 0.70;
    private static final double THRESHOLD_LOW = 0.55;

    public static AddressMatchResult compare(String address1, String address2) {
        AddressComponents c1 = AddressNormalizer.extractComponents(address1);
        AddressComponents c2 = AddressNormalizer.extractComponents(address2);

        ComponentMatchResult houseResult = compareHouseNumbers(c1.getHouseNumber(), c2.getHouseNumber());
        ComponentMatchResult streetResult = compareStreets(c1.getStreet(), c2.getStreet());
        ComponentMatchResult locationResult = compareLocations(c1, c2);

        List<String> conflicts = detectConflicts(houseResult, streetResult, locationResult);

        boolean hasHardContradiction = conflicts.stream().anyMatch(c ->
                c.startsWith("HOUSE_NUMBER_MISMATCH") || c.startsWith("STREET_MISMATCH"));

        double score;
        if (hasHardContradiction) {
            score = 0.0;
        } else {
            score = calculateScore(houseResult, streetResult, locationResult);
        }

        MatchLevel level = hasHardContradiction ? MatchLevel.DIFFERENT : determineLevel(score);
        boolean matched = level != MatchLevel.DIFFERENT;

        Map<String, ComponentMatchResult> componentResults = new LinkedHashMap<>();
        componentResults.put("houseNumber", houseResult);
        componentResults.put("street", streetResult);
        componentResults.put("location", locationResult);

        return AddressMatchResult.builder()
                .matched(matched)
                .score(Math.round(score * 100.0) / 100.0)
                .level(level)
                .normalizedAddress1(AddressNormalizer.normalize(address1))
                .normalizedAddress2(AddressNormalizer.normalize(address2))
                .components1(c1)
                .components2(c2)
                .componentResults(componentResults)
                .conflicts(conflicts)
                .build();
    }

    private static ComponentMatchResult compareHouseNumbers(String h1, String h2) {
        if (h1 == null && h2 == null) {
            return ComponentMatchResult.builder().matched(true).score(1.0).value1(null).value2(null).build();
        }
        if (h1 == null || h2 == null) {
            return ComponentMatchResult.builder()
                    .matched(true).score(1.0).value1(h1).value2(h2).build();
        }
        boolean match = h1.equals(h2);
        return ComponentMatchResult.builder()
                .matched(match).score(match ? 1.0 : 0.0).value1(h1).value2(h2).build();
    }

    private static ComponentMatchResult compareStreets(String s1, String s2) {
        if (s1 == null && s2 == null) {
            return ComponentMatchResult.builder().matched(true).score(1.0).value1(null).value2(null).build();
        }
        if (s1 == null || s2 == null) {
            return ComponentMatchResult.builder()
                    .matched(true).score(1.0).value1(s1).value2(s2).build();
        }

        if (s1.equals(s2)) {
            return ComponentMatchResult.builder()
                    .matched(true).score(1.0).value1(s1).value2(s2).build();
        }

        Set<String> tokens1 = new LinkedHashSet<>(Arrays.asList(s1.split("\\s+")));
        Set<String> tokens2 = new LinkedHashSet<>(Arrays.asList(s2.split("\\s+")));

        Set<String> smaller = tokens1.size() <= tokens2.size() ? tokens1 : tokens2;
        Set<String> larger = tokens1.size() <= tokens2.size() ? tokens2 : tokens1;

        if (larger.containsAll(smaller)) {
            double tokenScore = (double) smaller.size() / larger.size();
            return ComponentMatchResult.builder()
                    .matched(tokenScore > 0.5).score(tokenScore).value1(s1).value2(s2).build();
        }

        double similarity = jaroWinkler(s1, s2);
        return ComponentMatchResult.builder()
                .matched(similarity > 0.8).score(similarity).value1(s1).value2(s2).build();
    }

    private static ComponentMatchResult compareLocations(AddressComponents c1, AddressComponents c2) {
        String[][] pairs = {
                {c1.getWard(), c2.getWard()},
                {c1.getDistrict(), c2.getDistrict()},
                {c1.getCity(), c2.getCity()},
                {c1.getCountry(), c2.getCountry()}
        };

        int compared = 0;
        int matched = 0;
        int conflicted = 0;

        for (String[] pair : pairs) {
            if (pair[0] != null && !pair[0].isBlank() && pair[1] != null && !pair[1].isBlank()) {
                compared++;
                if (pair[0].equals(pair[1])) {
                    matched++;
                } else {
                    conflicted++;
                }
            }
        }

        if (compared == 0) {
            boolean c1HasLocation = hasAnyLocation(c1);
            boolean c2HasLocation = hasAnyLocation(c2);
            double locScore = (c1HasLocation != c2HasLocation) ? 0.5 : 1.0;
            return ComponentMatchResult.builder().matched(true).score(locScore)
                    .value1(formatLocationSummary(c1)).value2(formatLocationSummary(c2)).build();
        }

        double score = (double) matched / compared;
        return ComponentMatchResult.builder()
                .matched(conflicted == 0).score(score)
                .value1(formatLocationSummary(c1)).value2(formatLocationSummary(c2)).build();
    }

    private static String formatLocationSummary(AddressComponents c) {
        List<String> parts = new ArrayList<>();
        if (c.getWard() != null) parts.add("ward=" + c.getWard());
        if (c.getDistrict() != null) parts.add("district=" + c.getDistrict());
        if (c.getCity() != null) parts.add("city=" + c.getCity());
        if (c.getCountry() != null) parts.add("country=" + c.getCountry());
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private static boolean hasAnyLocation(AddressComponents c) {
        return (c.getWard() != null && !c.getWard().isBlank())
                || (c.getDistrict() != null && !c.getDistrict().isBlank())
                || (c.getCity() != null && !c.getCity().isBlank())
                || (c.getCountry() != null && !c.getCountry().isBlank());
    }

    private static List<String> detectConflicts(ComponentMatchResult house,
                                                 ComponentMatchResult street,
                                                 ComponentMatchResult location) {
        List<String> conflicts = new ArrayList<>();

        if (house.getValue1() != null && house.getValue2() != null && !house.isMatched()) {
            conflicts.add("HOUSE_NUMBER_MISMATCH: " + house.getValue1() + " vs " + house.getValue2());
        }
        if (street.getValue1() != null && street.getValue2() != null && !street.isMatched()) {
            conflicts.add("STREET_MISMATCH: " + street.getValue1() + " vs " + street.getValue2());
        }
        if (!location.isMatched()) {
            conflicts.add("LOCATION_MISMATCH");
        }

        return conflicts;
    }

    private static double calculateScore(ComponentMatchResult house,
                                          ComponentMatchResult street,
                                          ComponentMatchResult location) {
        double houseScore = house.getScore();
        double streetScore = street.getScore();
        double locationScore = location.getScore();

        return houseScore * WEIGHT_HOUSE + streetScore * WEIGHT_STREET + locationScore * WEIGHT_LOCATION;
    }

    private static MatchLevel determineLevel(double score) {
        if (score >= THRESHOLD_EXACT) return MatchLevel.EXACT;
        if (score >= THRESHOLD_HIGH) return MatchLevel.HIGH;
        if (score >= THRESHOLD_MEDIUM) return MatchLevel.MEDIUM;
        if (score >= THRESHOLD_LOW) return MatchLevel.LOW;
        return MatchLevel.DIFFERENT;
    }

    static double jaroWinkler(String s1, String s2) {
        if (s1 == null || s2 == null) return 0.0;
        if (s1.equals(s2)) return 1.0;

        int len1 = s1.length();
        int len2 = s2.length();
        if (len1 == 0 || len2 == 0) return 0.0;

        int matchDistance = Math.max(len1, len2) / 2 - 1;
        if (matchDistance < 0) matchDistance = 0;

        boolean[] s1Matches = new boolean[len1];
        boolean[] s2Matches = new boolean[len2];

        int matches = 0;
        int transpositions = 0;

        for (int i = 0; i < len1; i++) {
            int start = Math.max(0, i - matchDistance);
            int end = Math.min(i + matchDistance + 1, len2);
            for (int j = start; j < end; j++) {
                if (s2Matches[j] || s1.charAt(i) != s2.charAt(j)) continue;
                s1Matches[i] = true;
                s2Matches[j] = true;
                matches++;
                break;
            }
        }

        if (matches == 0) return 0.0;

        int k = 0;
        for (int i = 0; i < len1; i++) {
            if (!s1Matches[i]) continue;
            while (!s2Matches[k]) k++;
            if (s1.charAt(i) != s2.charAt(k)) transpositions++;
            k++;
        }

        double jaro = ((double) matches / len1
                + (double) matches / len2
                + (double) (matches - transpositions / 2.0) / matches) / 3.0;

        int prefix = 0;
        for (int i = 0; i < Math.min(4, Math.min(len1, len2)); i++) {
            if (s1.charAt(i) == s2.charAt(i)) prefix++;
            else break;
        }

        return jaro + prefix * 0.1 * (1 - jaro);
    }
}
