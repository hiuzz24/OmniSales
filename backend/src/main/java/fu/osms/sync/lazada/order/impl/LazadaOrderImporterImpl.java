package fu.osms.sync.lazada.order.impl;

import fu.osms.channel.entity.Channel;
import fu.osms.common.enums.PlatformType;
import fu.osms.sync.lazada.order.*;
import fu.osms.sync.order.importing.OrderImportOutcome;
import fu.osms.sync.order.pull.ManualOrderPostImportService;
import fu.osms.sync.order.pull.OrderPullBatchResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LazadaOrderImporterImpl implements LazadaOrderImporter {
    private static final int PAGE_SIZE = 100;
    private final LazadaOrderApiService apiService;
    private final LazadaOrderMapper mapper;
    private final LazadaOrderPersistenceService persistenceService;
    private final ManualOrderPostImportService postImportService;

    @Override public PlatformType platform() { return PlatformType.LAZADA; }

    @Override
    public OrderPullBatchResult pull(Channel channel, OffsetDateTime from, OffsetDateTime to) {
        int total = 0, success = 0, failed = 0, offset = 0;
        List<String> errors = new ArrayList<>();
        while (true) {
            List<String> ids;
            try {
                ids = apiService.listOrderIds(channel, from, to, offset, PAGE_SIZE);
            } catch (Exception e) {
                failed++;
                errors.add("[PAGE] Lazada orders offset " + offset + ": " + message(e));
                break;
            }
            if (ids.isEmpty()) break;
            total += ids.size();
            for (String id : ids) {
                try {
                    Map<String, Object> detail = apiService.getOrder(channel, id);
                    OrderImportOutcome outcome = persistenceService.write(channel, mapper.map(
                            LazadaOrderStatusContext.manual(detail), detail, apiService.getOrderItems(channel, id)));
                    postImportService.publish(outcome);
                    success++;
                } catch (Exception e) {
                    failed++;
                    errors.add("[ORDER] " + id + ": " + message(e));
                }
            }
            if (ids.size() < PAGE_SIZE) break;
            offset += ids.size();
        }
        return new OrderPullBatchResult(total, success, failed, errors);
    }

    private String message(Exception e) { return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); }
}
