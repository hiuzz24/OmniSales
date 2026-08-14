package fu.osms.system.service;

import fu.osms.audit.repository.AuditLogRepository;
import fu.osms.system.repository.SystemLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogRetentionService {

    private final AuditLogRepository auditLogRepository;
    private final SystemLogRepository systemLogRepository;
    private final SystemSettingService systemSettingService;

    @Scheduled(cron = "${app.logs.cleanup-cron:0 0 3 * * *}")
    @Transactional
    public void deleteExpiredLogs() {
        int retentionDays = systemSettingService.getInteger("audit_log_retention_days", 180);
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(Math.max(retentionDays, 1));
        long deletedAuditLogs = auditLogRepository.deleteByPerformedAtBefore(cutoff);
        long deletedSystemLogs = systemLogRepository.deleteByLoggedAtBefore(cutoff);
        if (deletedAuditLogs + deletedSystemLogs > 0) {
            log.info("Deleted {} audit logs and {} system logs older than {} days",
                    deletedAuditLogs, deletedSystemLogs, retentionDays);
        }
    }
}
