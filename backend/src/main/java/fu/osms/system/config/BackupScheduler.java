package fu.osms.system.config;

import fu.osms.system.service.BackupService;
import fu.osms.system.service.SystemSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class BackupScheduler {

    private final BackupService backupService;
    private final SystemSettingService systemSettingService;

    // Chạy tự động sao lưu lúc 2:00 AM hàng ngày
    @Scheduled(cron = "${app.backup.cron:0 0 2 * * ?}")
    public void executeScheduledBackup() {
        if (!systemSettingService.getBoolean("backup_schedule_enabled", true)) {
            log.debug("Bỏ qua sao lưu định kỳ vì cấu hình backup_schedule_enabled đang tắt");
            return;
        }
        log.info("Bắt đầu thực thi tiến trình sao lưu cơ sở dữ liệu định kỳ...");
        try {
            backupService.createBackup("SYSTEM", "SCHEDULED");
            log.info("Hoàn tất sao lưu cơ sở dữ liệu định kỳ.");
        } catch (Exception e) {
            log.error("Lỗi trong quá trình tự động sao lưu định kỳ", e);
        }
    }
}
