package fu.osms.sync.tiktok.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.channel.entity.Channel;
import fu.osms.sync.tiktok.TikTokAuthorizedApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TikTokOrderApiServiceImpl Tests")
class TikTokOrderApiServiceImplTest {

    @Mock private TikTokAuthorizedApiClient tikTokApiClient;

    private TikTokOrderApiServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TikTokOrderApiServiceImpl(tikTokApiClient, new ObjectMapper());
    }

    private Channel channelWithCipher() {
        return Channel.builder()
                .id(UUID.randomUUID())
                .metadata(Map.of("shopCipher", "cipher-1"))
                .build();
    }

    @Test
    @DisplayName("getOrderDetails: throws IllegalArgumentException when orderIds size > 50")
    void getOrderDetails_tooMany() {
        Channel channel = channelWithCipher();
        List<String> ids = java.util.stream.IntStream.range(0, 51)
                .mapToObj(i -> "O" + i).toList();
        assertThatThrownBy(() -> service.getOrderDetails(channel, ids))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 and 50");
    }

    @Test
    @DisplayName("getOrderDetails: throws IllegalArgumentException when orderIds is empty or null")
    void getOrderDetails_empty() {
        Channel channel = channelWithCipher();
        assertThatThrownBy(() -> service.getOrderDetails(channel, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.getOrderDetails(channel, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("getOrderDetails: returns mapped orders in the same order as input")
    void getOrderDetails_happy() {
        Channel channel = channelWithCipher();
        when(tikTokApiClient.executeGet(eq(channel.getId()), eq("/order/202309/orders"), any()))
                .thenReturn("{\"code\":\"0\",\"data\":{\"orders\":[" +
                        "{\"id\":\"O-2\",\"status\":\"P\"}," +
                        "{\"id\":\"O-1\",\"status\":\"Q\"}]}}");

        List<Map<String, Object>> result = service.getOrderDetails(channel, List.of("O-1", "O-2"));

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).containsEntry("id", "O-1");
        assertThat(result.get(1)).containsEntry("id", "O-2");
    }

    @Test
    @DisplayName("getOrderDetails: throws IllegalStateException when TikTok omits a requested ID")
    void getOrderDetails_missingId() {
        Channel channel = channelWithCipher();
        when(tikTokApiClient.executeGet(eq(channel.getId()), anyString(), any()))
                .thenReturn("{\"code\":\"0\",\"data\":{\"orders\":[{\"id\":\"O-1\"}]}}");

        assertThatThrownBy(() -> service.getOrderDetails(channel, List.of("O-1", "O-2")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing IDs");
    }

    @Test
    @DisplayName("getOrderDetails: throws IllegalStateException when TikTok returns an unexpected order")
    void getOrderDetails_unexpected() {
        Channel channel = channelWithCipher();
        when(tikTokApiClient.executeGet(eq(channel.getId()), anyString(), any()))
                .thenReturn("{\"code\":\"0\",\"data\":{\"orders\":[" +
                        "{\"id\":\"O-1\"}," +
                        "{\"id\":\"O-OTHER\"}]}}");

        assertThatThrownBy(() -> service.getOrderDetails(channel, List.of("O-1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unexpected order");
    }

    @Test
    @DisplayName("getOrderDetails: throws IllegalStateException when TikTok shop_cipher is missing")
    void getOrderDetails_noCipher() {
        Channel channel = Channel.builder().id(UUID.randomUUID()).metadata(Map.of()).build();
        assertThatThrownBy(() -> service.getOrderDetails(channel, List.of("O-1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("shop cipher");
    }

    @Test
    @DisplayName("searchOrders: returns ids and next_page_token from data")
    void searchOrders_happy() {
        Channel channel = channelWithCipher();
        when(tikTokApiClient.executePost(eq(channel.getId()), eq("/order/202309/orders/search"), any(), anyString()))
                .thenReturn("{\"code\":\"0\",\"data\":{" +
                        "\"orders\":[{\"id\":\"O-1\"},{\"id\":\"O-2\"}]," +
                        "\"next_page_token\":\"next-1\"}}");

        TikTokOrderApiServiceImpl.OrderSearchPage page = service.searchOrders(
                channel,
                OffsetDateTime.now().minusDays(1),
                OffsetDateTime.now(),
                "tok");

        assertThat(page.orderIds()).containsExactly("O-1", "O-2");
        assertThat(page.nextPageToken()).isEqualTo("next-1");
    }

    @Test
    @DisplayName("shipPackage: posts to /packages/{id}/ship and returns parsed root")
    void shipPackage_happy() {
        Channel channel = channelWithCipher();
        when(tikTokApiClient.executePost(eq(channel.getId()), eq("/fulfillment/202309/packages/P-1/ship"), any(), eq("{}")))
                .thenReturn("{\"code\":\"0\",\"data\":{\"package_id\":\"P-1\",\"status\":\"SHIPPED\"}}");

        Map<String, Object> result = service.shipPackage(channel, "P-1");

        assertThat(result).containsEntry("data", Map.of("package_id", "P-1", "status", "SHIPPED"));
    }

    @Test
    @DisplayName("cancelOrder: posts JSON body with order_id and cancel_reason")
    void cancelOrder_happy() {
        Channel channel = channelWithCipher();
        when(tikTokApiClient.executePost(eq(channel.getId()),
                eq("/return_refund/202602/cancellations"), any(), anyString()))
                .thenReturn("{\"code\":\"0\",\"data\":{\"ok\":true}}");

        Map<String, Object> result = service.cancelOrder(channel, "O-1", "OUT_OF_STOCK");

        assertThat(result).containsKey("data");
    }

    @Test
    @DisplayName("cancelOrder: throws IllegalStateException when API returns non-0 code")
    void cancelOrder_error() {
        Channel channel = channelWithCipher();
        when(tikTokApiClient.executePost(eq(channel.getId()), anyString(), any(), anyString()))
                .thenReturn("{\"code\":900,\"message\":\"cannot cancel\"}");

        assertThatThrownBy(() -> service.cancelOrder(channel, "O-1", "REASON"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot cancel");
    }
}
