package fu.osms.system.service.impl;

import fu.osms.auth.entity.User;
import fu.osms.auth.repository.UserRepository;
import fu.osms.common.dto.PageResponse;
import fu.osms.common.utils.SecurityUtils;
import fu.osms.system.entity.BackupFile;
import fu.osms.system.repository.BackupFileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BackupServiceImpl - Unit Tests")
class BackupServiceImplTest {

    @Mock
    private BackupFileRepository backupFileRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private DataSource dataSource;

    @InjectMocks
    private BackupServiceImpl backupService;

    private MockedStatic<SecurityUtils> securityUtilsMock;
    private UUID sampleId;
    private BackupFile sampleBackupFile;
    private User mockUser;
    private File tempDir;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(backupService, "dbUrl", "jdbc:postgresql://localhost:5432/testdb");
        ReflectionTestUtils.setField(backupService, "dbUsername", "postgres");
        ReflectionTestUtils.setField(backupService, "dbPassword", "password");
        ReflectionTestUtils.setField(backupService, "customPgDumpPath", "");
        ReflectionTestUtils.setField(backupService, "customPgRestorePath", "");

        tempDir = new File(System.getProperty("java.io.tmpdir"), "test-backups-" + UUID.randomUUID());
        tempDir.mkdirs();
        ReflectionTestUtils.setField(backupService, "backupDirectory", tempDir.getAbsolutePath());

        sampleId = UUID.randomUUID();

        mockUser = User.builder()
                .id(UUID.randomUUID())
                .email("admin@test.com")
                .passwordHash("hashedPassword")
                .build();

        securityUtilsMock = mockStatic(SecurityUtils.class);
        securityUtilsMock.when(SecurityUtils::getCurrentUser).thenReturn(Optional.of(mockUser));
    }

    @AfterEach
    void tearDown() {
        if (securityUtilsMock != null) {
            securityUtilsMock.close();
        }
        if (tempDir != null && tempDir.exists()) {
            File[] files = tempDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    f.delete();
                }
            }
            tempDir.delete();
        }
    }

    @Test
    @DisplayName("getBackupList - returns paginated response")
    void getBackupList_returnsPaginatedResponse() {
        sampleBackupFile = BackupFile.builder()
                .id(sampleId)
                .filename("backup_OSMS_20240101_120000.backup")
                .filepath(tempDir.getAbsolutePath() + "/backup_OSMS_20240101_120000.backup")
                .fileSize(1024L)
                .type("MANUAL")
                .status("SUCCESS")
                .createdBy("admin@test.com")
                .build();

        Page<BackupFile> page = new PageImpl<>(List.of(sampleBackupFile));
        when(backupFileRepository.findAllByOrderByCreatedAtDesc(any(Pageable.class))).thenReturn(page);

        PageResponse<BackupFile> result = backupService.getBackupList(0, 10);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(backupFileRepository).findAllByOrderByCreatedAtDesc(any(Pageable.class));
    }

    @Test
    @DisplayName("deleteBackup - deletes record when file doesn't exist")
    void deleteBackup_fileNotExists() {
        sampleBackupFile = BackupFile.builder()
                .id(sampleId)
                .filename("backup_OSMS_20240101_120000.backup")
                .filepath(tempDir.getAbsolutePath() + "/nonexistent.backup")
                .fileSize(0L)
                .type("MANUAL")
                .status("SUCCESS")
                .createdBy("admin@test.com")
                .build();

        when(backupFileRepository.findById(sampleId)).thenReturn(Optional.of(sampleBackupFile));

        backupService.deleteBackup(sampleId);

        verify(backupFileRepository).delete(sampleBackupFile);
    }

    @Test
    @DisplayName("deleteBackup - not found throws exception")
    void deleteBackup_notFound() {
        when(backupFileRepository.findById(sampleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> backupService.deleteBackup(sampleId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Không tìm thấy tệp sao lưu");
    }

    @Test
    @DisplayName("getBackupFile - returns file when exists")
    void getBackupFile_returnsFile() throws Exception {
        File realFile = new File(tempDir, "test-backup.backup");
        realFile.createNewFile();

        sampleBackupFile = BackupFile.builder()
                .id(sampleId)
                .filename("test-backup.backup")
                .filepath(realFile.getAbsolutePath())
                .fileSize(100L)
                .type("MANUAL")
                .status("SUCCESS")
                .createdBy("admin@test.com")
                .build();

        when(backupFileRepository.findById(sampleId)).thenReturn(Optional.of(sampleBackupFile));

        File result = backupService.getBackupFile(sampleId);

        assertThat(result.getAbsolutePath()).isEqualTo(realFile.getAbsolutePath());
        assertThat(result.exists()).isTrue();
    }

    @Test
    @DisplayName("getBackupFile - not found throws exception")
    void getBackupFile_notFound() {
        when(backupFileRepository.findById(sampleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> backupService.getBackupFile(sampleId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Không tìm thấy tệp sao lưu");
    }

    @Test
    @DisplayName("getBackupFile - file not exists on disk throws exception")
    void getBackupFile_fileNotOnDisk() {
        sampleBackupFile = BackupFile.builder()
                .id(sampleId)
                .filename("nonexistent.backup")
                .filepath(tempDir.getAbsolutePath() + "/nonexistent.backup")
                .fileSize(0L)
                .type("MANUAL")
                .status("SUCCESS")
                .createdBy("admin@test.com")
                .build();

        when(backupFileRepository.findById(sampleId)).thenReturn(Optional.of(sampleBackupFile));

        assertThatThrownBy(() -> backupService.getBackupFile(sampleId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tệp sao lưu vật lý không tồn tại trên server");
    }

    @Test
    @DisplayName("restoreBackup - wrong password throws exception")
    void restoreBackup_wrongPassword() {
        sampleBackupFile = BackupFile.builder()
                .id(sampleId)
                .filename("test.backup")
                .filepath(tempDir.getAbsolutePath() + "/test.backup")
                .fileSize(100L)
                .type("MANUAL")
                .status("SUCCESS")
                .createdBy("admin@test.com")
                .build();

        when(passwordEncoder.matches("wrongpassword", "hashedPassword")).thenReturn(false);

        assertThatThrownBy(() -> backupService.restoreBackup(sampleId, "wrongpassword"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Mật khẩu xác nhận không chính xác");
    }

    @Test
    @DisplayName("restoreBackup - backup record not found throws exception")
    void restoreBackup_backupNotFound() {
        when(passwordEncoder.matches("password", "hashedPassword")).thenReturn(true);
        when(backupFileRepository.findById(sampleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> backupService.restoreBackup(sampleId, "password"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Không tìm thấy bản ghi sao lưu");
    }

    @Test
    @DisplayName("restoreBackup - physical file not found throws exception")
    void restoreBackup_physicalFileNotFound() {
        sampleBackupFile = BackupFile.builder()
                .id(sampleId)
                .filename("nonexistent.backup")
                .filepath(tempDir.getAbsolutePath() + "/nonexistent.backup")
                .fileSize(0L)
                .type("MANUAL")
                .status("SUCCESS")
                .createdBy("admin@test.com")
                .build();

        when(passwordEncoder.matches("password", "hashedPassword")).thenReturn(true);
        when(backupFileRepository.findById(sampleId)).thenReturn(Optional.of(sampleBackupFile));

        assertThatThrownBy(() -> backupService.restoreBackup(sampleId, "password"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tệp sao lưu vật lý không tồn tại trên server");
    }
}
