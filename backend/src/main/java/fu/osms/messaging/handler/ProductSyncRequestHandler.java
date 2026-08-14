package fu.osms.messaging.handler;

import fu.osms.common.enums.SyncStatus;
import fu.osms.messaging.dto.ProductSyncMessage;
import fu.osms.notification.repository.NotificationRepository;
import fu.osms.notification.service.NotificationService;
import fu.osms.sync.dto.SyncResult;
import fu.osms.sync.entity.SyncLog;
import fu.osms.sync.repository.SyncLogRepository;
import fu.osms.sync.service.ProductSyncOrchestratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductSyncRequestHandler {

    private static final String REQUEST_JOB_TYPE = "PRODUCT_SYNC_REQUEST";
    private static final String ENTITY_TYPE = "SYNC";

    private final ProductSyncOrchestratorService orchestratorService;
    private final SyncLogRepository syncLogRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;

    /** Xử lý một yêu cầu đồng bộ từ UI và kết thúc SyncLog cha đúng một lần. */
    public void handle(ProductSyncMessage message) {
        if (message.requestLogId() == null) {
            handleLegacy(message);
            return;
        }

        SyncLog request = requireRequest(message);
        if (request.getStatus() == SyncStatus.SYNCED || request.getStatus() == SyncStatus.FAILED) {
            notifyOutcome(request);
            log.info("[ProductSyncRequest] Ignored terminal request requestLogId={} status={}",
                    request.getId(), request.getStatus());
            return;
        }

        log.info("[ProductSyncRequest] Processing requestLogId={} messageId={} productId={} channelId={}",
                message.requestLogId(), message.messageId(), message.productId(), message.channelId());
        SyncResult result = message.channelId() == null
                ? orchestratorService.syncProductToAllChannels(message.productId())
                : orchestratorService.syncProductToChannel(message.productId(), message.channelId());

        request = syncLogRepository.findRequestDetailsById(message.requestLogId())
                .orElseThrow(() -> new IllegalStateException("Product sync request not found: " + message.requestLogId()));
        if (request.getStatus() == SyncStatus.SYNCED || request.getStatus() == SyncStatus.FAILED) {
            notifyOutcome(request);
            return;
        }

        request.setTotalItems(result.getTotalChannels());
        request.setSuccessCount(result.getSuccessCount());
        request.setFailCount(result.getFailedCount());
        request.setStatus(result.getFailedCount() > 0 ? SyncStatus.FAILED : SyncStatus.SYNCED);
        request.setErrorSummary(errorSummary(result));
        request.setCompletedAt(OffsetDateTime.now());
        request = syncLogRepository.save(request);
        notifyOutcome(request);
    }

    /** Tạo một thông báo không trùng cho yêu cầu cha đã kết thúc. */
    public void notifyOutcome(SyncLog request) {
        if (request == null || request.getId() == null || request.getTriggeredBy() == null
                || (request.getStatus() != SyncStatus.SYNCED && request.getStatus() != SyncStatus.FAILED)) {
            return;
        }
        String type = request.getStatus() == SyncStatus.SYNCED ? "SYNC" : "SYNC_FAILED";
        if (notificationRepository.existsByUserIdAndTypeAndEntityTypeAndEntityIdAndCreatedAtAfter(
                request.getTriggeredBy().getId(), type, ENTITY_TYPE, request.getId(), request.getStartedAt())) {
            return;
        }
        String productName = request.getProduct() == null ? "sản phẩm" : request.getProduct().getName();
        boolean success = request.getStatus() == SyncStatus.SYNCED;
        String title = success ? "Đồng bộ sản phẩm thành công" : "Đồng bộ sản phẩm có lỗi";
        String body = success
                ? "Đã đồng bộ " + productName + " lên " + request.getSuccessCount() + " kênh."
                : "Đồng bộ " + productName + ": " + request.getSuccessCount() + " thành công, "
                        + request.getFailCount() + " thất bại."
                        + errorSuffix(request.getErrorSummary());
        notificationService.createNotification(
                request.getTriggeredBy().getId(), type, title, body, ENTITY_TYPE, request.getId());
    }

    private SyncLog requireRequest(ProductSyncMessage message) {
        SyncLog request = syncLogRepository.findRequestDetailsById(message.requestLogId())
                .orElseThrow(() -> new IllegalStateException("Product sync request not found: " + message.requestLogId()));
        if (!REQUEST_JOB_TYPE.equals(request.getJobType())
                || request.getProduct() == null
                || !Objects.equals(request.getProduct().getId(), message.productId())) {
            throw new IllegalStateException("Product sync message does not match request log " + message.requestLogId());
        }
        return request;
    }

    private void handleLegacy(ProductSyncMessage message) {
        log.info("[ProductSyncRequest] Processing legacy message productId={} channelId={}",
                message.productId(), message.channelId());
        if (message.channelId() == null) {
            orchestratorService.syncProductToAllChannels(message.productId());
        } else {
            orchestratorService.syncProductToChannel(message.productId(), message.channelId());
        }
    }

    private String errorSummary(SyncResult result) {
        if (result.getFailedCount() <= 0) {
            return null;
        }
        String summary = result.getDetails().stream()
                .filter(detail -> !detail.isSuccess())
                .map(detail -> detail.getChannelName() + ": "
                        + Objects.toString(detail.getErrorMessage(), "Không xác định"))
                .collect(Collectors.joining("; "));
        return summary.length() <= 2000 ? summary : summary.substring(0, 2000);
    }

    private String errorSuffix(String error) {
        if (error == null || error.isBlank()) {
            return "";
        }
        String compact = error.length() <= 180 ? error : error.substring(0, 177) + "...";
        return " Chi tiết: " + compact;
    }
}
