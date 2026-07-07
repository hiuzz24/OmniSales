package fu.osms.system.service;

import fu.osms.common.dto.PageResponse;
import fu.osms.system.entity.BackupFile;

import java.io.File;
import java.util.UUID;

public interface BackupService {
    PageResponse<BackupFile> getBackupList(int page, int size);
    BackupFile createBackup(String actorEmail, String type);
    void deleteBackup(UUID id);
    File getBackupFile(UUID id);
    void restoreBackup(UUID id, String enteredPassword);
}
