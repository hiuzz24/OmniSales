package fu.osms.sync.webhook.impl;

import fu.osms.common.enums.PlatformType;
import fu.osms.orderreturn.model.OrderReturnSnapshot;
import fu.osms.orderreturn.service.OrderReturnPersistenceService;
import fu.osms.sync.entity.WebhookEvent;
import fu.osms.sync.service.PlatformReturnWebhookProcessor;
import fu.osms.sync.shopify.returning.ShopifyReturnGraphQlClient;
import fu.osms.sync.shopify.returning.ShopifyReturnSnapshotMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ShopifyReturnWebhookProcessor implements PlatformReturnWebhookProcessor {

    private static final Set<String> TOPICS = Set.of(
            "RETURNS_REQUEST", "RETURNS_APPROVE", "RETURNS_DECLINE", "RETURNS_UPDATE",
            "RETURNS_PROCESS", "RETURNS_CANCEL", "REFUNDS_CREATE");

    private final ShopifyReturnGraphQlClient client;
    private final ShopifyReturnSnapshotMapper mapper;
    private final OrderReturnPersistenceService persistenceService;
    private final ShopifyOrderWebhookProcessor orderWebhookProcessor;

    @Override
    public boolean supports(WebhookEvent event) {
        if (event.getPlatform() != PlatformType.SHOPIFY) {
            return false;
        }
        String eventType = normalize(event.getEventType());
        return TOPICS.contains(eventType)
                || ("ORDERS_UPDATED".equals(eventType) && hasEmbeddedReturns(event.getRawPayload()));
    }

    @Override
    public String process(WebhookEvent event) {
        String eventType = normalize(event.getEventType());
        List<String> returnIds = returnIds(event.getRawPayload(), eventType);
        if (returnIds.isEmpty()) {
            return "IGNORED";
        }

        if ("ORDERS_UPDATED".equals(eventType)) {
            orderWebhookProcessor.process(event);
        }

        boolean refundConfirmed = "REFUNDS_CREATE".equals(eventType);
        for (String returnId : returnIds) {
            Map<String, Object> remote = client.getReturn(event.getChannel().getId(), returnId);
            OrderReturnSnapshot snapshot = mapper.map(remote, event.getExternalEventId(), refundConfirmed);
            persistenceService.upsert(event.getChannel(), snapshot);
        }
        return "PROCESSED";
    }

    private List<String> returnIds(Map<String, Object> payload, String eventType) {
        Set<String> ids = new LinkedHashSet<>();
        if (eventType.startsWith("RETURNS_")) {
            addReturnId(ids, first(payload, "admin_graphql_api_id", "return_id", "returnId", "id"));
        }
        Object nested = payload.get("return");
        if (nested instanceof Map<?, ?> map) {
            addReturnId(ids, first(map, "admin_graphql_api_id", "return_id", "id"));
        }
        Object embedded = payload.get("returns");
        if (embedded instanceof List<?> returns) {
            for (Object value : returns) {
                if (value instanceof Map<?, ?> map) {
                    addReturnId(ids, first(map, "admin_graphql_api_id", "return_id", "id"));
                }
            }
        }
        return List.copyOf(ids);
    }

    private boolean hasEmbeddedReturns(Map<String, Object> payload) {
        Object value = payload.get("returns");
        return value instanceof List<?> returns && !returns.isEmpty();
    }

    private void addReturnId(Set<String> ids, Object value) {
        if (value == null || value.toString().isBlank()) {
            return;
        }
        String id = value.toString();
        ids.add(id.startsWith("gid://shopify/Return/")
                ? id
                : "gid://shopify/Return/" + id);
    }

    private Object first(Map<?, ?> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value != null && !value.toString().isBlank()) return value;
        }
        return null;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toUpperCase().replace('/', '_');
    }
}
