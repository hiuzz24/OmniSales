package fu.osms.sync.tiktok.order.impl;

import fu.osms.channel.entity.Channel;
import fu.osms.common.enums.PlatformType;
import fu.osms.sync.order.importing.OrderImportOutcome;
import fu.osms.sync.order.pull.ManualOrderPostImportService;
import fu.osms.sync.order.pull.OrderPullBatchResult;
import fu.osms.sync.tiktok.TikTokOrderApiService;
import fu.osms.sync.tiktok.order.*;
import fu.osms.sync.webhook.WebhookPayloadUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TikTokOrderImporterImpl implements TikTokOrderImporter {
    private static final int DETAIL_BATCH_SIZE = 50;
    private final TikTokOrderApiService apiService;
    private final TikTokOrderMapper mapper;
    private final TikTokOrderPersistenceService persistenceService;
    private final ManualOrderPostImportService postImportService;

    @Override public PlatformType platform() { return PlatformType.TIKTOK; }

    @Override
    public OrderPullBatchResult pull(Channel channel, OffsetDateTime from, OffsetDateTime to) {
        List<String> ids;
        try {
            ids = loadIds(channel, from, to);
        } catch (Exception e) {
            return new OrderPullBatchResult(0, 0, 1, List.of("[PAGE] TikTok order search: " + message(e)));
        }
        int success = 0, failed = 0;
        List<String> errors = new ArrayList<>();
        for (int start = 0; start < ids.size(); start += DETAIL_BATCH_SIZE) {
            List<String> batch = ids.subList(start, Math.min(ids.size(), start + DETAIL_BATCH_SIZE));
            List<Map<String, Object>> details;
            try {
                details = apiService.getOrderDetails(channel, batch);
            } catch (Exception e) {
                failed += batch.size();
                errors.add("[PAGE] TikTok detail batch: " + message(e));
                continue;
            }
            for (Map<String, Object> detail : details) {
                String id = WebhookPayloadUtils.text(WebhookPayloadUtils.firstPresent(detail, "id", "order_id"));
                try {
                    OrderImportOutcome outcome = persistenceService.write(TikTokOrderWriteContext.manual(channel), mapper.map(detail));
                    postImportService.publish(outcome);
                    success++;
                } catch (Exception e) {
                    failed++;
                    errors.add("[ORDER] " + id + ": " + message(e));
                }
            }
        }
        return new OrderPullBatchResult(ids.size(), success, failed, errors);
    }

    private List<String> loadIds(Channel channel, OffsetDateTime from, OffsetDateTime to) {
        List<String> ids = new ArrayList<>();
        String token = null;
        do {
            TikTokOrderApiService.OrderSearchPage page = apiService.searchOrders(channel, from, to, token);
            ids.addAll(page.orderIds());
            if (page.nextPageToken() != null && page.nextPageToken().equals(token)) {
                throw new IllegalStateException("[PAGE] TikTok returned a repeated page token");
            }
            token = page.nextPageToken();
        } while (token != null && !token.isBlank());
        return ids.stream().distinct().toList();
    }
    private String message(Exception e) { return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); }
}
