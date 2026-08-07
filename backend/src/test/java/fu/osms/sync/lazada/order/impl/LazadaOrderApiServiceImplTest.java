package fu.osms.sync.lazada.order.impl;

import fu.osms.channel.entity.Channel;
import fu.osms.sync.lazada.service.LazadaAuthorizedApiClient;
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
@DisplayName("LazadaOrderApiServiceImpl Tests")
class LazadaOrderApiServiceImplTest {

    @Mock private LazadaAuthorizedApiClient apiClient;

    private LazadaOrderApiServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LazadaOrderApiServiceImpl(apiClient);
    }

    private Channel channel() {
        return Channel.builder().id(UUID.randomUUID()).build();
    }

    @Test
    @DisplayName("getOrder: returns the 'data' field of the response as a copy map")
    void getOrder() {
        when(apiClient.executeGet(any(), anyString(), any())).thenReturn(
                "{\"code\":\"0\",\"data\":{\"order_id\":\"O-1\",\"status\":\"pending\"}}"
        );

        Map<String, Object> result = service.getOrder(channel(), "O-1");

        assertThat(result).containsEntry("order_id", "O-1");
        assertThat(result).containsEntry("status", "pending");
    }

    @Test
    @DisplayName("getOrder: throws IllegalStateException when code is not '0'")
    void getOrder_error() {
        when(apiClient.executeGet(any(), anyString(), any())).thenReturn(
                "{\"code\":\"99\",\"message\":\"not found\"}"
        );

        assertThatThrownBy(() -> service.getOrder(channel(), "X"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("getOrderItems: returns the 'data' list as maps, filters non-Map items")
    void getOrderItems() {
        when(apiClient.executeGet(any(), eq("/order/items/get"), any())).thenReturn(
                "{\"code\":\"0\",\"data\":[{\"sku\":\"S1\"},{\"sku\":\"S2\"},\"non-map\"]}"
        );

        List<Map<String, Object>> result = service.getOrderItems(channel(), "O-1");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(m -> m.get("sku")).containsExactly("S1", "S2");
    }

    @Test
    @DisplayName("getOrderItems: returns empty list when 'data' is not a list")
    void getOrderItems_nonListData() {
        when(apiClient.executeGet(any(), anyString(), any())).thenReturn(
                "{\"code\":\"0\",\"data\":\"oops\"}"
        );

        List<Map<String, Object>> result = service.getOrderItems(channel(), "O-1");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("listOrderIds: returns 'order_id' values from the 'orders' list, skipping blanks")
    void listOrderIds() {
        when(apiClient.executeGet(any(), eq("/orders/get"), any())).thenReturn(
                "{\"code\":\"0\",\"data\":{\"orders\":[" +
                        "{\"order_id\":\"A1\"}," +
                        "{\"order_id\":\"A2\"}," +
                        "{\"order_id\":\"\"}," +
                        "{\"id\":\"A3\"}," +
                        "{\"order_id\":null}]}}"
        );

        List<String> result = service.listOrderIds(
                channel(),
                OffsetDateTime.now().minusDays(1),
                OffsetDateTime.now(),
                0,
                10);

        assertThat(result).containsExactly("A1", "A2", "A3");
    }
}
