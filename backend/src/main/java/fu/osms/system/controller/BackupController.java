package fu.osms.system.controller;

import fu.osms.common.dto.ApiResponse;
import fu.osms.common.dto.PageResponse;
import fu.osms.common.utils.SecurityUtils;
import fu.osms.system.entity.BackupFile;
import fu.osms.system.service.BackupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/backups")
@RequiredArgsConstructor
public class BackupController {

    private final BackupService backupService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<BackupFile>>> getBackupList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        log.info("Yêu cầu xem danh sách tệp sao lưu. Page: {}, Size: {}", page, size);
        PageResponse<BackupFile> result = backupService.getBackupList(page, size);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<BackupFile>> createManualBackup() {
        String actorEmail = SecurityUtils.getCurrentUserLogin().orElse("SYSTEM");
        log.info("Yêu cầu sao lưu cơ sở dữ liệu thủ công từ: {}", actorEmail);
        BackupFile backupFile = backupService.createBackup(actorEmail, "MANUAL");
        
        if ("SUCCESS".equals(backupFile.getStatus())) {
            return ResponseEntity.ok(ApiResponse.success("Tạo bản sao lưu dữ liệu thành công", backupFile));
        } else {
            return ResponseEntity.status(500).body(ApiResponse.error(500, "Tạo bản sao lưu dữ liệu thất bại"));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteBackup(@PathVariable UUID id) {
        log.info("Yêu cầu xóa tệp sao lưu ID: {}", id);
        backupService.deleteBackup(id);
        return ResponseEntity.ok(ApiResponse.success("Xóa tệp sao lưu thành công", null));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadBackupFile(@PathVariable UUID id) {
        log.info("Yêu cầu tải về tệp sao lưu ID: {}", id);
        File file = backupService.getBackupFile(id);
        Resource resource = new FileSystemResource(file);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getName() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(file.length())
                .body(resource);
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<ApiResponse<Void>> restoreBackup(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        
        String password = body.get("password");
        if (password == null || password.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "Vui lòng cung cấp mật khẩu xác nhận"));
        }

        String actorEmail = SecurityUtils.getCurrentUserLogin().orElse("SYSTEM");
        log.info("Yêu cầu khôi phục hệ thống từ bản backup ID: {} bởi: {}", id, actorEmail);
        
        try {
            backupService.restoreBackup(id, password);
            return ResponseEntity.ok(ApiResponse.success("Khôi phục dữ liệu thành công! Hệ thống đã được thiết lập lại.", null));
        } catch (IllegalArgumentException e) {
            log.warn("Lỗi khôi phục: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        } catch (Exception e) {
            log.error("Lỗi nghiêm trọng khi khôi phục dữ liệu", e);
            return ResponseEntity.status(500).body(ApiResponse.error(500, "Lỗi khôi phục cơ sở dữ liệu: " + e.getMessage()));
        }
    }
}
