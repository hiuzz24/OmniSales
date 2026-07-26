package fu.osms.sync.service;

import fu.osms.auth.repository.UserRoleRepository;
import fu.osms.notification.service.NotificationService;
import fu.osms.sync.entity.SyncLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SyncAlertService {

    private static final String ENTITY_TYPE = "SYNC";
    private static final String SYNC_FAILED_TYPE = "SYNC_FAILED";

    private final UserRoleRepository userRoleRepository;
    private final NotificationService notificationService;

    @Transactional
    public void notifySyncFailure(SyncLog syncLog) {
        if (syncLog == null || syncLog.getId() == null) {
            return;
        }

        String channelName = syncLog.getChannel() != null ? syncLog.getChannel().getDisplayName() : "Kênh bán hàng";
        String jobDesc = getJobDescription(syncLog.getJobType());
        
        String title = jobDesc + " thất bại";
        String errorDetail = syncLog.getErrorSummary() != null ? syncLog.getErrorSummary() : "Không xác định";
        if (errorDetail.length() > 150) {
            errorDetail = errorDetail.substring(0, 147) + "...";
        }
        String body = "Đồng bộ trên kênh " + channelName + " thất bại. Chi tiết: " + errorDetail;

        userRoleRepository.findByRoleNameIn(List.of("OWNER", "OPERATIONS", "SYSTEM_ADMIN")).stream()
                .map(userRole -> userRole.getUser().getId())
                .distinct()
                .forEach(userId -> {
                    try {
                        notificationService.createNotification(
                                userId,
                                SYNC_FAILED_TYPE,
                                title,
                                body,
                                ENTITY_TYPE,
                                syncLog.getId());
                    } catch (Exception e) {
                        log.error("Failed to send sync failure notification to user: {}", userId, e);
                    }
                });
    }

    private String getJobDescription(String jobType) {
        if (jobType == null) return "Đồng bộ dữ liệu";
        switch (jobType) {
            case "PRODUCT_SYNC":
                return "Đồng bộ sản phẩm";
            case "LAZADA_IMPORT":
                return "Đồng bộ tải dữ liệu từ Lazada";
            case "LAZADA_LOCAL_CHANGES_SYNC":
                return "Đồng bộ thay đổi lên Lazada";
            case "MARKETPLACE_INVENTORY_AUTO_PUSH":
                return "Tự động đồng bộ tồn kho lên sàn";
            case "MARKETPLACE_INVENTORY_RECONCILE":
                return "Đối soát tồn kho với sàn";
            default:
                return "Đồng bộ dữ liệu";
        }
    }
}
