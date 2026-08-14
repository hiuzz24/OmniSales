package fu.osms.messaging.recovery;

import fu.osms.common.enums.SyncStatus;
import fu.osms.messaging.handler.ProductSyncRequestHandler;
import fu.osms.sync.entity.SyncLog;
import fu.osms.sync.repository.SyncLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductSyncRequestRecoverySweeper {

    private static final String JOB_TYPE = "PRODUCT_SYNC_REQUEST";
    private static final String STALE_ERROR = "Yêu cầu đồng bộ không hoàn tất sau 2 giờ";

    private final SyncLogRepository syncLogRepository;
    private final ProductSyncRequestHandler handler;

    @Scheduled(fixedDelayString = "${app.messaging.recovery.product-sync-delay-ms:300000}")
    /** Đánh dấu thất bại các yêu cầu cha bị bỏ dở mà không gọi lại API sàn. */
    public void sweep() {
        OffsetDateTime now = OffsetDateTime.now();
        List<SyncLog> stale = syncLogRepository.findStaleRequests(
                JOB_TYPE, SyncStatus.PENDING, now.minusHours(2), PageRequest.of(0, 50));
        for (SyncLog request : stale) {
            int updated = syncLogRepository.failPendingRequest(
                    request.getId(), SyncStatus.PENDING, SyncStatus.FAILED, STALE_ERROR, now);
            if (updated == 0) {
                continue;
            }
            try {
                syncLogRepository.findRequestDetailsById(request.getId()).ifPresent(handler::notifyOutcome);
            } catch (Exception exception) {
                log.error("[ProductSyncRecovery] Failed to notify requestLogId={}", request.getId(), exception);
            }
        }
    }
}
