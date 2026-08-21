package fu.osms.system.service.impl;

import fu.osms.auth.entity.User;
import fu.osms.auth.repository.UserRepository;
import fu.osms.common.dto.PageResponse;
import fu.osms.common.utils.SecurityUtils;
import fu.osms.system.entity.BackupFile;
import fu.osms.system.repository.BackupFileRepository;
import fu.osms.system.service.BackupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.sql.DataSource;
import com.zaxxer.hikari.HikariDataSource;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BackupServiceImpl implements BackupService {

    private static final String BACKUP_METADATA_TABLE = "backup_files";
    private static final Pattern BACKUP_METADATA_TOC_ENTRY =
            Pattern.compile("(?i).*\\b" + BACKUP_METADATA_TABLE + "\\b.*");

    private final BackupFileRepository backupFileRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    @Value("${spring.datasource.url}")
    private String dbUrl;

    @Value("${spring.datasource.username}")
    private String dbUsername;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    @Value("${app.backup.custom-pg-dump-path:}")
    private String customPgDumpPath;

    @Value("${app.backup.custom-pg-restore-path:}")
    private String customPgRestorePath;

    @Value("${app.backup.directory:backups}")
    private String backupDirectory;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BackupFile> getBackupList(int page, int size) {
        Page<BackupFile> filePage = backupFileRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
        return PageResponse.<BackupFile>builder()
                .content(filePage.getContent())
                .page(filePage.getNumber())
                .size(filePage.getSize())
                .totalElements(filePage.getTotalElements())
                .totalPages(filePage.getTotalPages())
                .first(filePage.isFirst())
                .last(filePage.isLast())
                .build();
    }

    @Override
    @Transactional
    public BackupFile createBackup(String actorEmail, String type) {
        File dir = new File(backupDirectory);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        Map<String, String> dbConfig = parseJdbcUrl(dbUrl);
        String host = dbConfig.get("host");
        String port = dbConfig.get("port");
        String dbName = dbConfig.get("database");

        String timestamp = OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = "backup_OSMS_" + timestamp + ".backup";
        File backupFile = new File(dir, filename);

        BackupFile entity = BackupFile.builder()
                .filename(filename)
                .filepath(backupFile.getAbsolutePath())
                .fileSize(0L)
                .type(type)
                .status("FAILED")
                .createdBy(actorEmail)
                .createdAt(OffsetDateTime.now())
                .build();

        try {
            List<String> commands = new ArrayList<>();
            commands.add(getPgDumpPath());
            commands.add("-h"); commands.add(host);
            commands.add("-p"); commands.add(port);
            commands.add("-U"); commands.add(dbUsername);
            commands.add("-F"); commands.add("c");
            commands.add("-b");
            // Danh mục file backup là dữ liệu vận hành hiện tại, không phải dữ liệu
            // nghiệp vụ cần quay ngược khi restore.
            commands.add("--exclude-table=public." + BACKUP_METADATA_TABLE);
            commands.add("-v");
            commands.add("-f"); commands.add(backupFile.getAbsolutePath());
            commands.add(dbName);

            log.info("Bắt đầu tạo backup: {}", String.join(" ", commands));
            ProcessBuilder pb = new ProcessBuilder(commands);
            pb.environment().put("PGPASSWORD", dbPassword);
            Process process = pb.start();

            // Đọc error stream của pg_dump
            try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    log.debug("[pg_dump] {}", line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode == 0 && backupFile.exists()) {
                entity.setFileSize(backupFile.length());
                entity.setStatus("SUCCESS");
                log.info("Sao lưu cơ sở dữ liệu thành công: {}", filename);
            } else {
                log.error("Lệnh pg_dump kết thúc với mã lỗi: {}", exitCode);
            }
        } catch (Exception e) {
            log.error("Lỗi khi chạy tiến trình sao lưu cơ sở dữ liệu", e);
        }

        return backupFileRepository.save(entity);
    }

    @Override
    @Transactional
    public void deleteBackup(UUID id) {
        BackupFile backupFile = backupFileRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tệp sao lưu: " + id));

        File file = new File(backupFile.getFilepath());
        if (file.exists()) {
            file.delete();
        }

        backupFileRepository.delete(backupFile);
        log.info("Đã xóa tệp sao lưu: {}", backupFile.getFilename());
    }

    @Override
    public File getBackupFile(UUID id) {
        BackupFile backupFile = backupFileRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tệp sao lưu: " + id));
        File file = new File(backupFile.getFilepath());
        if (!file.exists()) {
            throw new IllegalArgumentException("Tệp sao lưu vật lý không tồn tại trên server: " + backupFile.getFilename());
        }
        return file;
    }

    @Override
    public void restoreBackup(UUID id, String enteredPassword) {
        // 1. Xác thực người dùng hiện tại
        User currentUser = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new IllegalStateException("Phiên làm việc không hợp lệ"));

        if (!passwordEncoder.matches(enteredPassword, currentUser.getPasswordHash())) {
            throw new IllegalArgumentException("Mật khẩu xác nhận không chính xác");
        }

        BackupFile backupRecord = backupFileRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bản ghi sao lưu: " + id));

        File file = new File(backupRecord.getFilepath());
        if (!file.exists()) {
            throw new IllegalArgumentException("Tệp sao lưu vật lý không tồn tại trên server: " + backupRecord.getFilename());
        }

        Map<String, String> dbConfig = parseJdbcUrl(dbUrl);
        String host = dbConfig.get("host");
        String port = dbConfig.get("port");
        String dbName = dbConfig.get("database");

        // 2. Ngắt các kết nối active khác
        terminateConnections(dbName);

        // 3. Thực thi pg_restore
        File restoreListFile = null;
        try {
            // Các archive cũ có thể vẫn chứa backup_files. Tạo TOC đã lọc để
            // pg_restore không drop/restore bảng này và làm mất danh sách backup hiện tại.
            restoreListFile = createFilteredRestoreList(file);

            List<String> commands = new ArrayList<>();
            commands.add(getPgRestorePath());
            commands.add("-h"); commands.add(host);
            commands.add("-p"); commands.add(port);
            commands.add("-U"); commands.add(dbUsername);
            commands.add("-d"); commands.add(dbName);
            commands.add("-c");
            commands.add("--if-exists");
            commands.add("-L"); commands.add(restoreListFile.getAbsolutePath());
            commands.add("-v");
            commands.add(file.getAbsolutePath());

            log.info("Bắt đầu phục hồi dữ liệu từ file: {}", file.getName());
            ProcessBuilder pb = new ProcessBuilder(commands);
            pb.environment().put("PGPASSWORD", dbPassword);
            Process process = pb.start();

            // Capture toàn bộ stderr để vừa log chi tiết vừa đính kèm vào thông báo lỗi
            // nếu restore thực sự thất bại. pg_restore có thể ghi cảnh báo (warning) ra
            // stderr trong khi restore vẫn thành công — đây là hành vi bình thường.
            List<String> stderrLines = new ArrayList<>();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    stderrLines.add(line);
                    log.debug("[pg_restore] {}", line);
                }
            }

            int exitCode = process.waitFor();
            // Theo tài liệu pg_restore:
            //   0 = thành công hoàn toàn
            //   1 = thành công nhưng có cảnh báo (ví dụ: DROP IF EXISTS không tìm thấy object)
            //   2 = lỗi kết nối tới máy chủ
            //   3 = lỗi nghiêm trọng (fatal error)
            // Do đó chỉ coi exit code >= 2 là lỗi thực sự. Với exit code = 1, dữ liệu
            // vẫn được khôi phục, chỉ ghi log cảnh báo để admin theo dõi.
            if (exitCode == 1) {
                String warnings = stderrLines.stream()
                        .filter(line -> line != null && !line.isBlank())
                        .collect(Collectors.joining(" | "));
                log.warn("pg_restore hoàn tất với exit code 1 (cảnh báo, dữ liệu vẫn được khôi phục) khi restore file {}: {}",
                        file.getName(), warnings);
            } else if (exitCode != 0) {
                String detail = stderrLines.stream()
                        .filter(line -> line != null && !line.isBlank())
                        .limit(20)
                        .collect(Collectors.joining(" | "));
                throw new RuntimeException("Tiến trình pg_restore kết thúc với mã lỗi: " + exitCode
                        + (detail.isEmpty() ? "" : ". Chi tiết: " + detail));
            }
            log.info("Khôi phục cơ sở dữ liệu thành công từ file: {}", file.getName());

            // Làm sạch kết nối Hikari pool để các request tiếp theo lấy kết nối mới tinh
            if (dataSource instanceof HikariDataSource) {
                HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
                com.zaxxer.hikari.HikariPoolMXBean poolMXBean = hikariDataSource.getHikariPoolMXBean();
                if (poolMXBean != null) {
                    poolMXBean.softEvictConnections();
                    log.info("Đã làm sạch (soft evict) tất cả kết nối trong Hikari Connection Pool sau khi restore.");
                }
            }
        } catch (Exception e) {
            log.error("Lỗi trong quá trình khôi phục cơ sở dữ liệu", e);
            throw new RuntimeException("Lỗi khôi phục dữ liệu: " + e.getMessage());
        } finally {
            if (restoreListFile != null) {
                try {
                    Files.deleteIfExists(restoreListFile.toPath());
                } catch (Exception cleanupError) {
                    log.warn("Không thể xóa file danh sách restore tạm: {}", restoreListFile, cleanupError);
                }
            }
        }
    }

    private File createFilteredRestoreList(File archiveFile) throws Exception {
        File directory = archiveFile.getParentFile() != null ? archiveFile.getParentFile() : new File(backupDirectory);
        File listFile = File.createTempFile("pg_restore_", ".list", directory);
        try {
            ProcessBuilder listProcessBuilder = new ProcessBuilder(
                    getPgRestorePath(), "-l", archiveFile.getAbsolutePath());
            listProcessBuilder.redirectOutput(listFile);
            listProcessBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);

            Process listProcess = listProcessBuilder.start();
            int exitCode = listProcess.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("Không thể đọc danh sách nội dung file backup, mã lỗi: " + exitCode);
            }

            List<String> filteredLines = Files.readAllLines(listFile.toPath(), StandardCharsets.UTF_8)
                    .stream()
                    .map(line -> isBackupMetadataTocEntry(line) && !line.startsWith(";") ? ";" + line : line)
                    .toList();
            Files.write(listFile.toPath(), filteredLines, StandardCharsets.UTF_8);

            log.info("Đã loại bảng {} khỏi danh sách restore", BACKUP_METADATA_TABLE);
            return listFile;
        } catch (Exception e) {
            Files.deleteIfExists(listFile.toPath());
            throw e;
        }
    }

    static boolean isBackupMetadataTocEntry(String tocLine) {
        return tocLine != null && BACKUP_METADATA_TOC_ENTRY.matcher(tocLine).matches();
    }

    private void terminateConnections(String dbName) {
        try {
            jdbcTemplate.execute(String.format("""
                SELECT pg_terminate_backend(pg_stat_activity.pid)
                FROM pg_stat_activity
                WHERE pg_stat_activity.datname = '%s'
                  AND pid <> pg_backend_pid()
            """, dbName));
            log.info("Đã ngắt tất cả các kết nối DB khác tới: {}", dbName);
        } catch (Exception e) {
            log.warn("Không thể ngắt các kết nối khác (có thể do thiếu quyền hoặc DB local): {}", e.getMessage());
        }
    }

    private Map<String, String> parseJdbcUrl(String url) {
        Map<String, String> dbConfig = new HashMap<>();
        try {
            String cleanUrl = url.replace("jdbc:postgresql://", "");
            if (cleanUrl.contains("?")) {
                cleanUrl = cleanUrl.split("\\?")[0];
            }
            String[] parts = cleanUrl.split("/");
            String hostPort = parts[0];
            String dbName = parts[1];
            dbConfig.put("database", dbName);

            if (hostPort.contains(":")) {
                String[] hp = hostPort.split(":");
                dbConfig.put("host", hp[0]);
                dbConfig.put("port", hp[1]);
            } else {
                dbConfig.put("host", hostPort);
                dbConfig.put("port", "5432");
            }
        } catch (Exception e) {
            log.error("Lỗi phân tích JDBC URL: {}", url, e);
            dbConfig.put("host", "localhost");
            dbConfig.put("port", "5432");
            dbConfig.put("database", "OSMS");
        }
        return dbConfig;
    }

    private String getPgDumpPath() {
        if (customPgDumpPath != null && !customPgDumpPath.isEmpty()) {
            return customPgDumpPath;
        }

        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            File pgDir = new File("C:\\Program Files\\PostgreSQL");
            if (pgDir.exists() && pgDir.isDirectory()) {
                File[] versions = pgDir.listFiles();
                if (versions != null) {
                    Arrays.sort(versions, Comparator.comparing(File::getName).reversed());
                    for (File versionDir : versions) {
                        File exe = new File(versionDir, "bin\\pg_dump.exe");
                        if (exe.exists()) {
                            return exe.getAbsolutePath();
                        }
                    }
                }
            }
            return "pg_dump.exe";
        }
        return "pg_dump";
    }

    private String getPgRestorePath() {
        if (customPgRestorePath != null && !customPgRestorePath.isEmpty()) {
            return customPgRestorePath;
        }

        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            File pgDir = new File("C:\\Program Files\\PostgreSQL");
            if (pgDir.exists() && pgDir.isDirectory()) {
                File[] versions = pgDir.listFiles();
                if (versions != null) {
                    Arrays.sort(versions, Comparator.comparing(File::getName).reversed());
                    for (File versionDir : versions) {
                        File exe = new File(versionDir, "bin\\pg_restore.exe");
                        if (exe.exists()) {
                            return exe.getAbsolutePath();
                        }
                    }
                }
            }
            return "pg_restore.exe";
        }
        return "pg_restore";
    }
}
