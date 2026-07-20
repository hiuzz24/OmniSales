package fu.osms.system.service.impl;

import fu.osms.audit.entity.AuditLog;
import fu.osms.audit.repository.AuditLogRepository;
import fu.osms.common.dto.PageResponse;
import fu.osms.system.dto.SystemLogResponse;
import fu.osms.system.entity.SystemLog;
import fu.osms.system.repository.SystemLogProjection;
import fu.osms.system.repository.SystemLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SystemLogServiceImpl - Unit Tests")
class SystemLogServiceImplTest {

    @Mock
    private SystemLogRepository systemLogRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private SystemLogServiceImpl systemLogService;

    private UUID sampleId;
    private SystemLog sampleSystemLog;
    private AuditLog sampleAuditLog;

    @BeforeEach
    void setUp() {
        sampleId = UUID.randomUUID();

        Map<String, Object> contextMap = new HashMap<>();
        contextMap.put("key", "value");

        sampleSystemLog = SystemLog.builder()
                .id(sampleId)
                .level("INFO")
                .message("System test message")
                .context(contextMap)
                .build();

        Map<String, Object> changesMap = new HashMap<>();
        changesMap.put("field", "value");

        sampleAuditLog = AuditLog.builder()
                .id(sampleId)
                .actorEmail("admin@test.com")
                .action("CREATE")
                .entityType("Product")
                .changes(changesMap)
                .build();
    }

    @Test
    @DisplayName("searchLogs - returns paginated response")
    void searchLogs_returnsPaginatedResponse() {
        SystemLogProjection projection = createProjection(sampleId, "INFO", "Test message", "admin@test.com");
        Page<SystemLogProjection> page = new PageImpl<>(List.of(projection));

        when(systemLogRepository.searchSystemAndAuditLogs(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(page);

        PageResponse<SystemLogResponse> result = systemLogService.searchLogs(null, null, null, null, 0, 10);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(sampleId);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("searchLogs - with filters passes parameters to repository")
    void searchLogs_withFilters() {
        OffsetDateTime from = OffsetDateTime.now().minusDays(7);
        OffsetDateTime to = OffsetDateTime.now();

        SystemLogProjection projection = createProjection(sampleId, "ERROR", "Error message", "user@test.com");
        Page<SystemLogProjection> page = new PageImpl<>(List.of(projection));

        when(systemLogRepository.searchSystemAndAuditLogs(eq("ERROR"), eq("test"), eq(from), eq(to), any(Pageable.class)))
                .thenReturn(page);

        PageResponse<SystemLogResponse> result = systemLogService.searchLogs("ERROR", "test", from, to, 0, 10);

        assertThat(result.getContent()).hasSize(1);
        verify(systemLogRepository).searchSystemAndAuditLogs(eq("ERROR"), eq("test"), eq(from), eq(to), any(Pageable.class));
    }

    @Test
    @DisplayName("updateLog - system log found updates correctly")
    void updateLog_systemLogFound() {
        SystemLogResponse updateDto = SystemLogResponse.builder()
                .type("WARN")
                .message("Updated message")
                .ip("192.168.1.1")
                .details("Updated details")
                .build();

        when(systemLogRepository.findById(sampleId)).thenReturn(Optional.of(sampleSystemLog));
        when(systemLogRepository.save(any(SystemLog.class))).thenAnswer(inv -> inv.getArgument(0));

        SystemLogResponse result = systemLogService.updateLog(sampleId, updateDto);

        assertThat(result.getId()).isEqualTo(sampleId);
        assertThat(result.getType()).isEqualTo("WARN");
        assertThat(result.getUser()).isEqualTo("SYSTEM");
        verify(systemLogRepository).save(sampleSystemLog);
    }

    @Test
    @DisplayName("updateLog - audit log found updates correctly")
    void updateLog_auditLogFound() {
        SystemLogResponse updateDto = SystemLogResponse.builder()
                .type("INFO")
                .message("Updated audit message")
                .user("admin@test.com")
                .ip("192.168.1.100")
                .details("Audit details")
                .build();

        when(systemLogRepository.findById(sampleId)).thenReturn(Optional.empty());
        when(auditLogRepository.findById(sampleId)).thenReturn(Optional.of(sampleAuditLog));
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        SystemLogResponse result = systemLogService.updateLog(sampleId, updateDto);

        assertThat(result.getId()).isEqualTo(sampleId);
        verify(auditLogRepository).save(sampleAuditLog);
    }

    @Test
    @DisplayName("updateLog - not found throws IllegalArgumentException")
    void updateLog_notFound() {
        SystemLogResponse updateDto = SystemLogResponse.builder()
                .type("INFO")
                .message("Test")
                .build();

        when(systemLogRepository.findById(sampleId)).thenReturn(Optional.empty());
        when(auditLogRepository.findById(sampleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> systemLogService.updateLog(sampleId, updateDto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Không tìm thấy dòng nhật ký");
    }

    @Test
    @DisplayName("deleteLog - system log found deletes successfully")
    void deleteLog_systemLogFound() {
        when(systemLogRepository.existsById(sampleId)).thenReturn(true);

        systemLogService.deleteLog(sampleId);

        verify(systemLogRepository).deleteById(sampleId);
        verify(auditLogRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("deleteLog - audit log found deletes successfully")
    void deleteLog_auditLogFound() {
        when(systemLogRepository.existsById(sampleId)).thenReturn(false);
        when(auditLogRepository.existsById(sampleId)).thenReturn(true);

        systemLogService.deleteLog(sampleId);

        verify(auditLogRepository).deleteById(sampleId);
        verify(systemLogRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("deleteLog - not found throws IllegalArgumentException")
    void deleteLog_notFound() {
        when(systemLogRepository.existsById(sampleId)).thenReturn(false);
        when(auditLogRepository.existsById(sampleId)).thenReturn(false);

        assertThatThrownBy(() -> systemLogService.deleteLog(sampleId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Không tìm thấy dòng nhật ký có ID");
    }

    private SystemLogProjection createProjection(UUID id, String level, String message, String user) {
        return new SystemLogProjection() {
            @Override
            public UUID getId() { return id; }
            @Override
            public String getType() { return level; }
            @Override
            public String getMessage() { return message; }
            @Override
            public String getUser() { return user; }
            @Override
            public String getIp() { return "127.0.0.1"; }
            @Override
            public String getDetails() { return "details"; }
            @Override
            public Instant getTimestamp() { return Instant.now(); }
        };
    }
}
