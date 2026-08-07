package fu.osms.sync.lazada.order;

import fu.osms.sync.webhook.WebhookPayloadUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record LazadaOrderStatusContext(String reverseStatus, List<String> statuses) {
    public LazadaOrderStatusContext {
        statuses = statuses == null ? List.of() : List.copyOf(statuses);
    }

    public static LazadaOrderStatusContext webhook(Map<String, Object> payload) {
        String reverse = WebhookPayloadUtils.text(WebhookPayloadUtils.firstPresentInData(payload, "reverse_status"));
        String status = WebhookPayloadUtils.text(WebhookPayloadUtils.firstPresentInData(payload, "order_status"));
        return new LazadaOrderStatusContext(reverse, status == null ? List.of() : List.of(status));
    }

    public static LazadaOrderStatusContext manual(Map<String, Object> orderData) {
        List<String> values = new ArrayList<>();
        Object raw = orderData.get("statuses");
        if (raw instanceof Iterable<?> iterable) {
            iterable.forEach(value -> add(values, value));
        } else {
            add(values, raw);
        }
        return new LazadaOrderStatusContext(null, values);
    }

    private static void add(List<String> target, Object value) {
        String text = WebhookPayloadUtils.text(value);
        if (text != null && !text.isBlank()) {
            target.add(text);
        }
    }
}
