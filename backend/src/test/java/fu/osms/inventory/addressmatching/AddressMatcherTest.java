package fu.osms.inventory.addressmatching;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AddressMatcher Tests")
class AddressMatcherTest {

    @Nested
    @DisplayName("3 Dia Chi Thuc Te (TikTok / Lazada / Shopify)")
    class RealAddressesTests {

        private static final String TIKTOK =
                "138 P. Tran Binh, Tu Liem Ward, Ha Noi City, The Socialist Republic of Viet Nam";
        private static final String LAZADA = "138 Pho Tran Binh";
        private static final String SHOPIFY = "138 Pho Tran Binh, Ha Noi, Vietnam";

        @Test
        @DisplayName("TEST 1: TikTok vs Lazada -> MATCH")
        void tiktokVsLazada() {
            AddressMatchResult result = AddressMatcher.compare(TIKTOK, LAZADA);
            assertThat(result.isMatched()).isTrue();
            assertThat(result.getLevel()).isIn(MatchLevel.EXACT, MatchLevel.HIGH, MatchLevel.MEDIUM);
        }

        @Test
        @DisplayName("TEST 2: TikTok vs Shopify -> MATCH")
        void tiktokVsShopify() {
            AddressMatchResult result = AddressMatcher.compare(TIKTOK, SHOPIFY);
            assertThat(result.isMatched()).isTrue();
            assertThat(result.getLevel()).isIn(MatchLevel.EXACT, MatchLevel.HIGH, MatchLevel.MEDIUM);
        }

        @Test
        @DisplayName("TEST 3: Lazada vs Shopify -> MATCH")
        void lazadaVsShopify() {
            AddressMatchResult result = AddressMatcher.compare(LAZADA, SHOPIFY);
            assertThat(result.isMatched()).isTrue();
            assertThat(result.getLevel()).isIn(MatchLevel.EXACT, MatchLevel.HIGH, MatchLevel.MEDIUM);
        }

        @Test
        @DisplayName("All 3 addresses should have same normalized street and house number")
        void allShouldHaveSameStreetAndHouse() {
            AddressComponents c1 = AddressNormalizer.extractComponents(TIKTOK);
            AddressComponents c2 = AddressNormalizer.extractComponents(LAZADA);
            AddressComponents c3 = AddressNormalizer.extractComponents(SHOPIFY);

            assertThat(c1.getStreet()).isEqualTo("tran binh");
            assertThat(c2.getStreet()).isEqualTo("tran binh");
            assertThat(c3.getStreet()).isEqualTo("tran binh");

            assertThat(c1.getHouseNumber()).isEqualTo("138");
            assertThat(c2.getHouseNumber()).isEqualTo("138");
            assertThat(c3.getHouseNumber()).isEqualTo("138");
        }
    }

    @Nested
    @DisplayName("Contradiction Detection Tests")
    class ContradictionTests {

        @Test
        @DisplayName("TEST 4: Different house number -> DIFFERENT")
        void differentHouseNumber() {
            AddressMatchResult result = AddressMatcher.compare(
                    "138 Tran Binh, Ha Noi", "139 Tran Binh, Ha Noi");
            assertThat(result.isMatched()).isFalse();
            assertThat(result.getLevel()).isEqualTo(MatchLevel.DIFFERENT);
        }

        @Test
        @DisplayName("TEST 5: Different street -> DIFFERENT")
        void differentStreet() {
            AddressMatchResult result = AddressMatcher.compare(
                    "138 Tran Binh, Ha Noi", "138 Nguyen Trai, Ha Noi");
            assertThat(result.isMatched()).isFalse();
            assertThat(result.getLevel()).isEqualTo(MatchLevel.DIFFERENT);
        }

        @Test
        @DisplayName("TEST 9: Same house+street, different city -> not EXACT")
        void differentCity() {
            AddressMatchResult result = AddressMatcher.compare(
                    "138 Tran Binh", "138 Tran Binh, Hai Phong");
            assertThat(result.getLevel()).isNotEqualTo(MatchLevel.EXACT);
        }
    }

    @Nested
    @DisplayName("Missing Information Tests")
    class MissingInfoTests {

        @Test
        @DisplayName("TEST 6: Same house+street, one has city -> MATCH")
        void missingCityOneSide() {
            AddressMatchResult result = AddressMatcher.compare(
                    "138 Tran Binh", "138 Tran Binh, Ha Noi");
            assertThat(result.isMatched()).isTrue();
        }

        @Test
        @DisplayName("TEST 10: Same house+street, one has more location -> MATCH")
        void missingLocationOneSide() {
            AddressMatchResult result = AddressMatcher.compare(
                    "138 Tran Binh", "138 Tran Binh, Tu Liem Ward, Ha Noi City");
            assertThat(result.isMatched()).isTrue();
        }
    }

    @Nested
    @DisplayName("Prefix Normalization Tests")
    class PrefixTests {

        @Test
        @DisplayName("TEST 7: So prefix + Duong prefix vs Pho prefix -> MATCH")
        void differentPrefixes() {
            AddressMatchResult result = AddressMatcher.compare(
                    "So 138, Duong Tran Binh, Ha Noi", "138 Pho Tran Binh");
            assertThat(result.isMatched()).isTrue();
        }

        @Test
        @DisplayName("Street prefix should not affect matching")
        void streetPrefixesShouldNotMatter() {
            AddressComponents c1 = AddressNormalizer.extractComponents("138 Pho Tran Binh");
            AddressComponents c2 = AddressNormalizer.extractComponents("138 Duong Tran Binh");
            AddressComponents c3 = AddressNormalizer.extractComponents("138 Tran Binh");

            assertThat(c1.getStreet()).isEqualTo("tran binh");
            assertThat(c2.getStreet()).isEqualTo("tran binh");
            assertThat(c3.getStreet()).isEqualTo("tran binh");
        }
    }

    @Nested
    @DisplayName("House Number Normalization Tests")
    class HouseNumberTests {

        @Test
        @DisplayName("TEST 8: 138A vs 138 A -> MATCH")
        void houseNumberWithLetter() {
            AddressMatchResult result = AddressMatcher.compare(
                    "138A Tran Binh", "138 A Tran Binh");
            assertThat(result.isMatched()).isTrue();
        }

        @Test
        @DisplayName("House number with slash should match")
        void houseNumberWithSlash() {
            AddressComponents c1 = AddressNormalizer.extractComponents("138/2 Tran Binh");
            AddressComponents c2 = AddressNormalizer.extractComponents("138/2 Tran Binh");
            assertThat(c1.getHouseNumber()).isEqualTo(c2.getHouseNumber());
        }

        @Test
        @DisplayName("House number with range should match")
        void houseNumberWithRange() {
            AddressComponents c = AddressNormalizer.extractComponents("138-140 Tran Binh");
            assertThat(c.getHouseNumber()).isEqualTo("138-140");
        }
    }

    @Nested
    @DisplayName("Score and Level Tests")
    class ScoreTests {

        @Test
        @DisplayName("Exact match should have score 1.0 and level EXACT")
        void exactMatch() {
            AddressMatchResult result = AddressMatcher.compare(
                    "138 Tran Binh, Ha Noi", "138 Tran Binh, Ha Noi");
            assertThat(result.getScore()).isEqualTo(1.0);
            assertThat(result.getLevel()).isEqualTo(MatchLevel.EXACT);
        }

        @Test
        @DisplayName("Completely different addresses should be DIFFERENT")
        void completelyDifferent() {
            AddressMatchResult result = AddressMatcher.compare(
                    "138 Tran Binh, Ha Noi", "456 Le Loi, TP HCM");
            assertThat(result.getLevel()).isEqualTo(MatchLevel.DIFFERENT);
        }

        @Test
        @DisplayName("Missing location should not reduce score significantly")
        void missingLocation() {
            AddressMatchResult result = AddressMatcher.compare(
                    "138 Tran Binh", "138 Tran Binh");
            assertThat(result.getScore()).isEqualTo(1.0);
            assertThat(result.getLevel()).isEqualTo(MatchLevel.EXACT);
        }
    }

    @Nested
    @DisplayName("Jaro-Winkler Tests")
    class JaroWinklerTests {

        @Test
        @DisplayName("Identical strings should have similarity 1.0")
        void identicalStrings() {
            assertThat(AddressMatcher.jaroWinkler("hello", "hello")).isEqualTo(1.0);
        }

        @Test
        @DisplayName("Similar strings should have high similarity")
        void similarStrings() {
            double sim = AddressMatcher.jaroWinkler("tran binh", "tran binh ha noi");
            assertThat(sim).isGreaterThan(0.7);
        }

        @Test
        @DisplayName("Different strings should have low similarity")
        void differentStrings() {
            double sim = AddressMatcher.jaroWinkler("tran binh", "nguyen trai");
            assertThat(sim).isLessThan(0.6);
        }
    }

    @Nested
    @DisplayName("Conflicts Detection Tests")
    class ConflictTests {

        @Test
        @DisplayName("Should list conflicts when house numbers differ")
        void houseConflict() {
            AddressMatchResult result = AddressMatcher.compare(
                    "138 Tran Binh", "139 Tran Binh");
            assertThat(result.getConflicts()).isNotEmpty();
            assertThat(result.getConflicts().get(0)).contains("HOUSE_NUMBER_MISMATCH");
        }

        @Test
        @DisplayName("Should list conflicts when streets differ")
        void streetConflict() {
            AddressMatchResult result = AddressMatcher.compare(
                    "138 Tran Binh", "138 Nguyen Trai");
            assertThat(result.getConflicts()).isNotEmpty();
            assertThat(result.getConflicts().get(0)).contains("STREET_MISMATCH");
        }

        @Test
        @DisplayName("No conflicts for matching addresses")
        void noConflicts() {
            AddressMatchResult result = AddressMatcher.compare(
                    "138 Tran Binh, Ha Noi", "138 Tran Binh");
            assertThat(result.getConflicts()).isEmpty();
        }
    }
}
