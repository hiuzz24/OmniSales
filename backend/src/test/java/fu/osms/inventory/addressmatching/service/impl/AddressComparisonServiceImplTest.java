package fu.osms.inventory.addressmatching.service.impl;

import fu.osms.inventory.addressmatching.AddressMatchResult;
import fu.osms.inventory.addressmatching.MatchLevel;
import fu.osms.inventory.addressmatching.dto.response.AddressGroupResponse;
import fu.osms.inventory.addressmatching.service.AddressComparisonService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AddressComparisonServiceImpl Tests")
class AddressComparisonServiceImplTest {

    private AddressComparisonService service;

    @BeforeEach
    void setUp() {
        service = new AddressComparisonServiceImpl();
    }

    @Nested
    @DisplayName("compare() Tests")
    class CompareTests {

        @Test
        @DisplayName("Should return matched=true for same addresses")
        void shouldMatchSameAddresses() {
            AddressMatchResult result = service.compare("138 Tran Binh, Ha Noi", "138 Tran Binh, Ha Noi");
            assertThat(result.isMatched()).isTrue();
            assertThat(result.getLevel()).isEqualTo(MatchLevel.EXACT);
        }

        @Test
        @DisplayName("Should return matched=false for different house numbers")
        void shouldNotMatchDifferentHouseNumbers() {
            AddressMatchResult result = service.compare("138 Tran Binh", "139 Tran Binh");
            assertThat(result.isMatched()).isFalse();
        }

        @Test
        @DisplayName("Should return matched=false for different streets")
        void shouldNotMatchDifferentStreets() {
            AddressMatchResult result = service.compare("138 Tran Binh", "138 Nguyen Trai");
            assertThat(result.isMatched()).isFalse();
        }

        @Test
        @DisplayName("Should handle TikTok vs Lazada")
        void shouldHandleTikTokVsLazada() {
            AddressMatchResult result = service.compare(
                    "138 P. Tran Binh, Tu Liem Ward, Ha Noi City, The Socialist Republic of Viet Nam",
                    "138 Pho Tran Binh");
            assertThat(result.isMatched()).isTrue();
        }

        @Test
        @DisplayName("Should handle TikTok vs Shopify")
        void shouldHandleTikTokVsShopify() {
            AddressMatchResult result = service.compare(
                    "138 P. Tran Binh, Tu Liem Ward, Ha Noi City, The Socialist Republic of Viet Nam",
                    "138 Pho Tran Binh, Ha Noi, Vietnam");
            assertThat(result.isMatched()).isTrue();
        }

        @Test
        @DisplayName("Should handle Lazada vs Shopify")
        void shouldHandleLazadaVsShopify() {
            AddressMatchResult result = service.compare(
                    "138 Pho Tran Binh",
                    "138 Pho Tran Binh, Ha Noi, Vietnam");
            assertThat(result.isMatched()).isTrue();
        }
    }

    @Nested
    @DisplayName("groupAddresses() Tests")
    class GroupTests {

        @Test
        @DisplayName("Should group all matching addresses into one group")
        void shouldGroupMatchingAddresses() {
            List<String> addresses = List.of(
                    "138 P. Tran Binh, Tu Liem Ward, Ha Noi City, Vietnam",
                    "138 Pho Tran Binh",
                    "138 Pho Tran Binh, Ha Noi, Vietnam"
            );

            AddressGroupResponse response = service.groupAddresses(addresses);

            assertThat(response.isAllSameAddress()).isTrue();
            assertThat(response.getTotalGroups()).isEqualTo(1);
            assertThat(response.getGroups()).hasSize(1);
            assertThat(response.getGroups().get(0).getAddresses()).hasSize(3);
        }

        @Test
        @DisplayName("Should separate non-matching addresses into different groups")
        void shouldSeparateDifferentAddresses() {
            List<String> addresses = List.of(
                    "138 Tran Binh, Ha Noi",
                    "456 Le Loi, TP HCM"
            );

            AddressGroupResponse response = service.groupAddresses(addresses);

            assertThat(response.isAllSameAddress()).isFalse();
            assertThat(response.getTotalGroups()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should handle empty list")
        void shouldHandleEmptyList() {
            AddressGroupResponse response = service.groupAddresses(List.of());
            assertThat(response.getTotalGroups()).isEqualTo(0);
            assertThat(response.isAllSameAddress()).isTrue();
        }

        @Test
        @DisplayName("Should handle single address")
        void shouldHandleSingleAddress() {
            AddressGroupResponse response = service.groupAddresses(List.of("138 Tran Binh"));
            assertThat(response.getTotalGroups()).isEqualTo(1);
            assertThat(response.isAllSameAddress()).isTrue();
        }

        @Test
        @DisplayName("Should handle null input")
        void shouldHandleNull() {
            AddressGroupResponse response = service.groupAddresses(null);
            assertThat(response.getTotalGroups()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should handle mixed matching and non-matching addresses")
        void shouldHandleMixedAddresses() {
            List<String> addresses = List.of(
                    "138 Tran Binh, Ha Noi",
                    "138 Tran Binh, Vietnam",
                    "456 Le Loi, HCM"
            );

            AddressGroupResponse response = service.groupAddresses(addresses);

            assertThat(response.getTotalGroups()).isGreaterThanOrEqualTo(2);
        }
    }
}
