package fu.osms.inventory.addressmatching;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AddressNormalizer Tests")
class AddressNormalizerTest {

    @Nested
    @DisplayName("normalize() Tests")
    class NormalizeTests {

        @Test
        @DisplayName("Should lowercase and strip diacritics")
        void shouldLowercaseAndStripDiacritics() {
            String result = AddressNormalizer.normalize("Hà Nội");
            assertThat(result).isEqualTo("ha noi");
        }

        @Test
        @DisplayName("Should normalize whitespace")
        void shouldNormalizeWhitespace() {
            String result = AddressNormalizer.normalize("138   Trần   Bình");
            assertThat(result).isEqualTo("138 tran binh");
        }

        @Test
        @DisplayName("Should normalize country aliases - Viet Nam")
        void shouldNormalizeVietNam() {
            String result = AddressNormalizer.normalize("Viet Nam");
            assertThat(result).contains("vietnam");
        }

        @Test
        @DisplayName("Should normalize country aliases - The Socialist Republic of Viet Nam")
        void shouldNormalizeFullCountryName() {
            String result = AddressNormalizer.normalize("The Socialist Republic of Viet Nam");
            assertThat(result).contains("vietnam");
        }

        @Test
        @DisplayName("Should handle null and blank input")
        void shouldHandleNullAndBlank() {
            assertThat(AddressNormalizer.normalize(null)).isEmpty();
            assertThat(AddressNormalizer.normalize("")).isEmpty();
            assertThat(AddressNormalizer.normalize("   ")).isEmpty();
        }
    }

    @Nested
    @DisplayName("extractComponents() Tests")
    class ExtractComponentsTests {

        @Test
        @DisplayName("Should extract house number from simple address")
        void shouldExtractHouseNumber() {
            AddressComponents c = AddressNormalizer.extractComponents("138 Trần Bình");
            assertThat(c.getHouseNumber()).isEqualTo("138");
        }

        @Test
        @DisplayName("Should extract house number with letter suffix")
        void shouldExtractHouseNumberWithLetter() {
            AddressComponents c = AddressNormalizer.extractComponents("138A Trần Bình");
            assertThat(c.getHouseNumber()).isEqualTo("138a");
        }

        @Test
        @DisplayName("Should extract house number with space before letter")
        void shouldExtractHouseNumberWithSpace() {
            AddressComponents c = AddressNormalizer.extractComponents("138 A Trần Bình");
            assertThat(c.getHouseNumber()).isEqualTo("138a");
        }

        @Test
        @DisplayName("Should extract house number with So prefix")
        void shouldExtractHouseNumberWithSoPrefix() {
            AddressComponents c = AddressNormalizer.extractComponents("Số 138, Đường Trần Bình, Hà Nội");
            assertThat(c.getHouseNumber()).isEqualTo("138");
        }

        @Test
        @DisplayName("Should extract house number with slash")
        void shouldExtractHouseNumberWithSlash() {
            AddressComponents c = AddressNormalizer.extractComponents("138/2 Trần Bình");
            assertThat(c.getHouseNumber()).isEqualTo("138/2");
        }

        @Test
        @DisplayName("Should extract house number with range")
        void shouldExtractHouseNumberWithRange() {
            AddressComponents c = AddressNormalizer.extractComponents("138-140 Trần Bình");
            assertThat(c.getHouseNumber()).isEqualTo("138-140");
        }

        @Test
        @DisplayName("Should extract street name stripping Pho prefix")
        void shouldStripPhoPrefix() {
            AddressComponents c = AddressNormalizer.extractComponents("138 Phố Trần Bình");
            assertThat(c.getStreet()).isEqualTo("tran binh");
        }

        @Test
        @DisplayName("Should extract street name stripping Duong prefix")
        void shouldStripDuongPrefix() {
            AddressComponents c = AddressNormalizer.extractComponents("138 Đường Trần Bình");
            assertThat(c.getStreet()).isEqualTo("tran binh");
        }

        @Test
        @DisplayName("Should extract street name stripping D. prefix")
        void shouldStripD_Dot_Prefix() {
            AddressComponents c = AddressNormalizer.extractComponents("138 Đ. Trần Bình");
            assertThat(c.getStreet()).isEqualTo("tran binh");
        }

        @Test
        @DisplayName("Should extract street name with no prefix")
        void shouldHandleNoPrefix() {
            AddressComponents c = AddressNormalizer.extractComponents("138 Trần Bình");
            assertThat(c.getStreet()).isEqualTo("tran binh");
        }

        @Test
        @DisplayName("Should extract street from TikTok format with P. prefix")
        void shouldHandleTikTokFormat() {
            AddressComponents c = AddressNormalizer.extractComponents(
                    "138 P. Trần Bình, Tu Liem Ward, Ha Noi City, The Socialist Republic of Viet Nam");
            assertThat(c.getHouseNumber()).isEqualTo("138");
            assertThat(c.getStreet()).isEqualTo("tran binh");
        }

        @Test
        @DisplayName("Should extract city from address with city indicator")
        void shouldExtractCity() {
            AddressComponents c = AddressNormalizer.extractComponents("138 Trần Bình, Hà Nội");
            assertThat(c.getCity()).isEqualTo("ha noi");
        }

        @Test
        @DisplayName("Should extract country from address")
        void shouldExtractCountry() {
            AddressComponents c = AddressNormalizer.extractComponents("138 Trần Bình, Hà Nội, Vietnam");
            assertThat(c.getCountry()).isEqualTo("vietnam");
        }

        @Test
        @DisplayName("Should extract ward from TikTok format")
        void shouldExtractWard() {
            AddressComponents c = AddressNormalizer.extractComponents(
                    "138 P. Trần Bình, Tu Liem Ward, Ha Noi City, Vietnam");
            assertThat(c.getWard()).isEqualTo("tu liem");
        }

        @Test
        @DisplayName("Full TikTok address extraction")
        void fullTikTokExtraction() {
            AddressComponents c = AddressNormalizer.extractComponents(
                    "138 P. Trần Bình, Tu Liem Ward, Ha Noi City, The Socialist Republic of Viet Nam");
            assertThat(c.getHouseNumber()).isEqualTo("138");
            assertThat(c.getStreet()).isEqualTo("tran binh");
            assertThat(c.getWard()).isEqualTo("tu liem");
            assertThat(c.getCity()).isEqualTo("ha noi");
            assertThat(c.getCountry()).isEqualTo("vietnam");
        }

        @Test
        @DisplayName("Full Lazada address extraction")
        void fullLazadaExtraction() {
            AddressComponents c = AddressNormalizer.extractComponents("138 Phố Trần Bình");
            assertThat(c.getHouseNumber()).isEqualTo("138");
            assertThat(c.getStreet()).isEqualTo("tran binh");
        }

        @Test
        @DisplayName("Full Shopify address extraction")
        void fullShopifyExtraction() {
            AddressComponents c = AddressNormalizer.extractComponents("138 Phố Trần Bình, Hà Nội, Vietnam");
            assertThat(c.getHouseNumber()).isEqualTo("138");
            assertThat(c.getStreet()).isEqualTo("tran binh");
            assertThat(c.getCity()).isEqualTo("ha noi");
            assertThat(c.getCountry()).isEqualTo("vietnam");
        }

        @Test
        @DisplayName("Should handle null input gracefully")
        void shouldHandleNullInput() {
            AddressComponents c = AddressNormalizer.extractComponents(null);
            assertThat(c.getHouseNumber()).isNull();
            assertThat(c.getStreet()).isNull();
        }

        @Test
        @DisplayName("Should extract city without explicit indicator")
        void shouldExtractCityWithoutIndicator() {
            AddressComponents c = AddressNormalizer.extractComponents("138 Trần Bình, Hải Phòng");
            assertThat(c.getCity()).isEqualTo("hai phong");
        }
    }

    @Nested
    @DisplayName("stripDiacritics() Tests")
    class StripDiacriticsTests {

        @Test
        @DisplayName("Should strip Vietnamese diacritics")
        void shouldStripVietnameseDiacritics() {
            assertThat(AddressNormalizer.stripDiacritics("Trần Bình")).isEqualTo("Tran Binh");
        }

        @Test
        @DisplayName("Should handle null")
        void shouldHandleNull() {
            assertThat(AddressNormalizer.stripDiacritics(null)).isNull();
        }

        @Test
        @DisplayName("Should handle string without diacritics")
        void shouldHandleNoDiacritics() {
            assertThat(AddressNormalizer.stripDiacritics("Hello")).isEqualTo("Hello");
        }
    }
}
