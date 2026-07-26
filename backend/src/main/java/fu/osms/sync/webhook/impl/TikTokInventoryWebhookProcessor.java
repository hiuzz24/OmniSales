package fu.osms.sync.webhook.impl;

import fu.osms.channel.entity.ChannelProductVariant;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.common.enums.PlatformType;
import fu.osms.sync.entity.WebhookEvent;
import fu.osms.sync.inventory.InventoryObservation;
import fu.osms.sync.inventory.InventoryReconciliationService;
import fu.osms.sync.service.PlatformCatalogWebhookProcessor;
import fu.osms.sync.webhook.WebhookPayloadUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TikTokInventoryWebhookProcessor implements PlatformCatalogWebhookProcessor {

    private static final String EVENT_TYPE = "TIKTOK_INVENTORY_CHANGED";

    private final ChannelProductVariantRepository mappingRepository;
    private final InventoryReconciliationService reconciliationService;

    @Override
    public PlatformType getPlatform() {
        return PlatformType.TIKTOK;
    }

    @Override
    public boolean supports(WebhookEvent event) {
        return EVENT_TYPE.equalsIgnoreCase(event.getEventType());
    }

    @Override
    public String process(WebhookEvent event) {
        Map<String, Object> data = WebhookPayloadUtils.copyMap(event.getRawPayload().get("data"));
        String productId = text(data.get("product_id"));
        String skuId = text(data.get("sku_id"));
        Map<String, Object> snapshot =
                WebhookPayloadUtils.copyMap(data.get("quantity_snapshot_after_change"));
        Object remoteValue = snapshot.get("total_available_quantity");
        if (!hasText(productId) || !hasText(skuId) || remoteValue == null) {
            throw new IllegalArgumentException(
                    "TikTok inventory webhook is missing product_id, sku_id, or total_available_quantity");
        }

        ChannelProductVariant mapping = mappingRepository
                .findActiveByChannelIdAndExternalVariantId(event.getChannel().getId(), skuId)
                .orElse(null);
        if (mapping == null) {
            log.info("[TikTokInventoryWebhook] Ignore unmapped SKU channelId={} productId={} skuId={}",
                    event.getChannel().getId(), productId, skuId);
            return "IGNORED";
        }
        String mappedProductId = mapping.getChannelProduct().getExternalProductId();
        if (!productId.equals(mappedProductId)) {
            log.warn("[TikTokInventoryWebhook] Ignore product mismatch channelId={} productId={} mappedProductId={} skuId={}",
                    event.getChannel().getId(), productId, mappedProductId, skuId);
            return "IGNORED";
        }

        reconciliationService.observe(new InventoryObservation(
                mapping.getId(),
                PlatformType.TIKTOK,
                Math.max(WebhookPayloadUtils.integer(remoteValue, 0), 0),
                observedAt(data, event),
                primaryWarehouseId(event, mapping),
                event.getId()
        ));
        return "PROCESSED";
    }

    private OffsetDateTime observedAt(Map<String, Object> data, WebhookEvent event) {
        String value = text(data.get("occurred_at"));
        if (hasText(value)) {
            try {
                return OffsetDateTime.parse(value);
            } catch (DateTimeParseException ignored) {
                log.warn("[TikTokInventoryWebhook] Invalid occurred_at={} eventId={}", value, event.getId());
            }
        }
        return event.getReceivedAt() == null ? OffsetDateTime.now() : event.getReceivedAt();
    }

    private String primaryWarehouseId(WebhookEvent event, ChannelProductVariant mapping) {
        Map<String, Object> channelMetadata = event.getChannel().getMetadata();
        String configured = firstText(channelMetadata, "tiktokWarehouseId", "defaultTikTokWarehouseId");
        if (hasText(configured)) {
            return configured;
        }
        Object value = mapping.getMetadata() == null
                ? null
                : mapping.getMetadata().get("tiktokWarehouseIds");
        if (value instanceof List<?> warehouseIds) {
            return warehouseIds.stream()
                    .map(String::valueOf)
                    .filter(this::hasText)
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    private String firstText(Map<String, Object> values, String... keys) {
        if (values == null) {
            return null;
        }
        for (String key : keys) {
            String value = text(values.get(key));
            if (hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String text(Object value) {
        return value == null ? null : value.toString();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank() && !"null".equalsIgnoreCase(value);
    }
}
