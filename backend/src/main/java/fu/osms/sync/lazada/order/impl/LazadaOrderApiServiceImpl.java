package fu.osms.sync.lazada.order.impl;

import fu.osms.channel.entity.Channel;
import fu.osms.sync.lazada.order.LazadaOrderApiService;
import fu.osms.sync.lazada.service.LazadaAuthorizedApiClient;
import fu.osms.sync.webhook.WebhookPayloadUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LazadaOrderApiServiceImpl implements LazadaOrderApiService {

    private final LazadaAuthorizedApiClient apiClient;

    @Override
    public Map<String, Object> getOrder(Channel channel, String orderId) {
        Map<String, Object> body = get(channel, "/order/get", Map.of("order_id", orderId));
        return WebhookPayloadUtils.copyMap(body.get("data"));
    }

    @Override
    public List<Map<String, Object>> getOrderItems(Channel channel, String orderId) {
        Map<String, Object> body = get(channel, "/order/items/get", Map.of("order_id", orderId));
        return maps(body.get("data"));
    }

    @Override
    public List<String> listOrderIds(Channel channel, OffsetDateTime from, OffsetDateTime to, int offset, int limit) {
        Map<String, String> params = Map.of(
                "created_after", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(from),
                "created_before", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(to),
                "offset", String.valueOf(offset),
                "limit", String.valueOf(limit));
        Map<String, Object> body = get(channel, "/orders/get", params);
        Map<String, Object> data = WebhookPayloadUtils.copyMap(body.get("data"));
        return maps(data.get("orders")).stream()
                .map(order -> text(WebhookPayloadUtils.firstPresent(order, "order_id", "id")))
                .filter(value -> value != null && !value.isBlank()).toList();
    }

    private Map<String, Object> get(Channel channel, String path, Map<String, String> params) {
        String response = apiClient.executeGet(channel.getId(), path, params);
        Map<String, Object> body = WebhookPayloadUtils.parseObject(response, "Lazada order response is invalid");
        String code = text(body.get("code"));
        if (code != null && !"0".equals(code)) {
            throw new IllegalStateException("Lazada order API returned error: "
                    + text(WebhookPayloadUtils.firstPresent(body, "message", "msg")));
        }
        return body;
    }

    private List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(Map.class::isInstance).map(WebhookPayloadUtils::copyMap).toList();
    }

    private String text(Object value) { return WebhookPayloadUtils.text(value); }
}
