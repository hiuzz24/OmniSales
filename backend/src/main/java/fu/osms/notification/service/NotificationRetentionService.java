package fu.osms.notification.service;

import fu.osms.notification.repository.NotificationRepository;
import fu.osms.system.service.SystemSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationRetentionService {

    private final NotificationRepository notificationRepository;
    private final SystemSettingService systemSettingService;

    @Scheduled(cron = "${app.notifications.cleanup-cron:0 30 2 * * *}")
    @Transactional
    public void deleteExpiredNotifications() {
        int retentionDays = systemSettingService.getInteger("notification_retention_days", 90);
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(Math.max(retentionDays, 1));
        long deleted = notificationRepository.deleteByCreatedAtBefore(cutoff);
        if (deleted > 0) {
            log.info("Deleted {} notifications older than {} days", deleted, retentionDays);
        }
    }
}
