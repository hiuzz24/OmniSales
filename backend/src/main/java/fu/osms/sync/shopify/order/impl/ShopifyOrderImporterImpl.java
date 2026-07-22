package fu.osms.sync.shopify.order.impl;

import fu.osms.channel.entity.Channel;
import fu.osms.common.enums.PlatformType;
import fu.osms.sync.order.importing.OrderImportOutcome;
import fu.osms.sync.order.pull.ManualOrderPostImportService;
import fu.osms.sync.order.pull.OrderPullBatchResult;
import fu.osms.sync.shopify.order.*;
import fu.osms.sync.webhook.WebhookPayloadUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ShopifyOrderImporterImpl implements ShopifyOrderImporter {
    private final ShopifyOrderApiClient apiClient;
    private final ShopifyOrderMapper mapper;
    private final ShopifyOrderPersistenceService persistenceService;
    private final ManualOrderPostImportService postImportService;

    @Override public PlatformType platform() { return PlatformType.SHOPIFY; }

    @Override
    public OrderPullBatchResult pull(Channel channel, OffsetDateTime from, OffsetDateTime to) {
        int total = 0, success = 0, failed = 0;
        List<String> errors = new ArrayList<>();
        ShopifyOrderPage page;
        try {
            page = apiClient.firstPage(channel, from, to);
        } catch (Exception e) {
            return new OrderPullBatchResult(0, 0, 1, List.of("[PAGE] Shopify first page: " + message(e)));
        }
        while (page != null) {
            for (Map<String, Object> raw : page.orders()) {
                String id = WebhookPayloadUtils.text(WebhookPayloadUtils.firstPresent(raw, "id", "order_id"));
                total++;
                try {
                    OrderImportOutcome outcome = persistenceService.write(channel, mapper.mapManual(raw));
                    postImportService.publish(outcome);
                    success++;
                } catch (Exception e) {
                    failed++;
                    errors.add("[ORDER] " + id + ": " + message(e));
                }
            }
            if (page.nextPageInfo() == null || page.nextPageInfo().isBlank()) {
                page = null;
            } else {
                try {
                    page = apiClient.nextPage(channel, page.nextPageInfo());
                } catch (Exception e) {
                    failed++;
                    errors.add("[PAGE] Shopify next page: " + message(e));
                    page = null;
                }
            }
        }
        return new OrderPullBatchResult(total, success, failed, errors);
    }
    private String message(Exception e) { return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); }
}
