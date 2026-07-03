package fu.osms.audit.service.impl;

import fu.osms.audit.dto.response.AuditLogResponse;
import fu.osms.audit.entity.AuditLog;
import fu.osms.audit.repository.AuditLogRepository;
import fu.osms.auth.entity.User;
import fu.osms.auth.repository.UserRepository;
import fu.osms.common.dto.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AuditServiceImpl Tests")
class AuditServiceImplTest {

    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private UserRepository userRepository;
    @InjectMocks
    private AuditServiceImpl auditService;

    private ObjectMapper objectMapper;
    private UUID userId;
    private UUID entityId;
    private User user;
    private AuditLog auditLog;
    private AuditLogResponse auditLogResponse;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        auditService = new AuditServiceImpl(auditLogRepository, userRepository, objectMapper);

        userId = UUID.randomUUID();
        entityId = UUID.randomUUID();

        user = User.builder()
                .id(userId)
                .email("manager@osms.vn")
                .fullName("Manager User")
                .build();

        Map<String, Object> changes = new HashMap<>();
        Map<String, Object> statusChange = new HashMap<>();
        statusChange.put("old", "PENDING");
        statusChange.put("new", "CONFIRMED");
        changes.put("status", statusChange);
        Map<String, Object> quantityChange = new HashMap<>();
        quantityChange.put("old", 10);
        quantityChange.put("new", 15);
        changes.put("quantity", quantityChange);

        auditLog = AuditLog.builder()
                .id(UUID.randomUUID())
                .actor(user)
                .actorEmail("manager@osms.vn")
                .action("UPDATE")
                .entityType("INVENTORY")
                .entityId(entityId)
                .entityName("SKU-001")
                .changes(changes)
                .performedAt(OffsetDateTime.now())
                .build();

        auditLogResponse = AuditLogResponse.builder()
                .id(auditLog.getId())
                .actorId(userId)
                .actorEmail("manager@osms.vn")
                .action("UPDATE")
                .entityType("INVENTORY")
                .entityId(entityId)
                .entityName("SKU-001")
                .changes(changes)
                .performedAt(auditLog.getPerformedAt())
                .build();
    }

    // =========================================================
    // getAll() Tests
    // =========================================================
    @Nested
    @DisplayName("getAll() Tests")
    class GetAllTests {

        @Test
        @DisplayName("Should get all audit logs with pagination")
        void shouldGetAllAuditLogsWithPagination() {
            Page<AuditLog> logPage = new PageImpl<>(List.of(auditLog), PageRequest.of(0, 10), 1);
            when(auditLogRepository.findAll(any(PageRequest.class))).thenReturn(logPage);

            PageResponse<AuditLogResponse> result = auditService.getAll(0, 10);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should return empty page when no logs exist")
        void shouldReturnEmptyPageWhenNoLogsExist() {
            Page<AuditLog> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
            when(auditLogRepository.findAll(any(PageRequest.class))).thenReturn(emptyPage);

            PageResponse<AuditLogResponse> result = auditService.getAll(0, 10);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isEqualTo(0);
        }
    }

    // =========================================================
    // getByEntity() Tests
    // =========================================================
    @Nested
    @DisplayName("getByEntity() Tests")
    class GetByEntityTests {

        @Test
        @DisplayName("Should get audit logs by entity type and ID")
        void shouldGetAuditLogsByEntityTypeAndId() {
            Page<AuditLog> logPage = new PageImpl<>(List.of(auditLog), PageRequest.of(0, 10), 1);
            when(auditLogRepository.findByEntityTypeAndEntityId(eq("INVENTORY"), eq(entityId), any(PageRequest.class)))
                    .thenReturn(logPage);

            PageResponse<AuditLogResponse> result = auditService.getByEntity("INVENTORY", entityId, 0, 10);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getEntityId()).isEqualTo(entityId);
        }

        @Test
        @DisplayName("Should return empty when entity has no logs")
        void shouldReturnEmptyWhenEntityHasNoLogs() {
            Page<AuditLog> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
            when(auditLogRepository.findByEntityTypeAndEntityId(anyString(), any(UUID.class), any(PageRequest.class)))
                    .thenReturn(emptyPage);

            PageResponse<AuditLogResponse> result = auditService.getByEntity("INVENTORY", entityId, 0, 10);

            assertThat(result.getContent()).isEmpty();
        }
    }

    // =========================================================
    // getByActor() Tests
    // =========================================================
    @Nested
    @DisplayName("getByActor() Tests")
    class GetByActorTests {

        @Test
        @DisplayName("Should get audit logs by actor ID")
        void shouldGetAuditLogsByActorId() {
            Page<AuditLog> logPage = new PageImpl<>(List.of(auditLog), PageRequest.of(0, 10), 1);
            when(auditLogRepository.findByActorId(eq(userId), any(PageRequest.class))).thenReturn(logPage);

            PageResponse<AuditLogResponse> result = auditService.getByActor(userId, 0, 10);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getActorId()).isEqualTo(userId);
        }

        @Test
        @DisplayName("Should return empty when actor has no logs")
        void shouldReturnEmptyWhenActorHasNoLogs() {
            Page<AuditLog> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
            when(auditLogRepository.findByActorId(any(UUID.class), any(PageRequest.class))).thenReturn(emptyPage);

            PageResponse<AuditLogResponse> result = auditService.getByActor(userId, 0, 10);

            assertThat(result.getContent()).isEmpty();
        }
    }

    // =========================================================
    // getByDateRange() Tests
    // =========================================================
    @Nested
    @DisplayName("getByDateRange() Tests")
    class GetByDateRangeTests {

        @Test
        @DisplayName("Should get audit logs by date range")
        void shouldGetAuditLogsByDateRange() {
            OffsetDateTime from = OffsetDateTime.now().minusDays(7);
            OffsetDateTime to = OffsetDateTime.now();

            Page<AuditLog> logPage = new PageImpl<>(List.of(auditLog), PageRequest.of(0, 10), 1);
            when(auditLogRepository.findByPerformedAtBetween(eq(from), eq(to), any(PageRequest.class)))
                    .thenReturn(logPage);

            PageResponse<AuditLogResponse> result = auditService.getByDateRange(from, to, 0, 10);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("Should return empty when no logs in date range")
        void shouldReturnEmptyWhenNoLogsInDateRange() {
            OffsetDateTime from = OffsetDateTime.now().minusDays(30);
            OffsetDateTime to = OffsetDateTime.now().minusDays(20);

            Page<AuditLog> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
            when(auditLogRepository.findByPerformedAtBetween(any(), any(), any(PageRequest.class)))
                    .thenReturn(emptyPage);

            PageResponse<AuditLogResponse> result = auditService.getByDateRange(from, to, 0, 10);

            assertThat(result.getContent()).isEmpty();
        }
    }

    // =========================================================
    // getByEntityType() Tests
    // =========================================================
    @Nested
    @DisplayName("getByEntityType() Tests")
    class GetByEntityTypeTests {

        @Test
        @DisplayName("Should get audit logs by entity type")
        void shouldGetAuditLogsByEntityType() {
            Page<AuditLog> logPage = new PageImpl<>(List.of(auditLog), PageRequest.of(0, 10), 1);
            when(auditLogRepository.findByEntityType(eq("INVENTORY"), any(PageRequest.class))).thenReturn(logPage);

            PageResponse<AuditLogResponse> result = auditService.getByEntityType("INVENTORY", 0, 10);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getEntityType()).isEqualTo("INVENTORY");
        }

        @Test
        @DisplayName("Should get audit logs for different entity types")
        void shouldGetAuditLogsForDifferentEntityTypes() {
            // Test ORDER entity type
            AuditLog orderLog = AuditLog.builder()
                    .id(UUID.randomUUID())
                    .actor(user)
                    .actorEmail("manager@osms.vn")
                    .action("CREATE")
                    .entityType("ORDER")
                    .entityId(UUID.randomUUID())
                    .entityName("ORD-001")
                    .performedAt(OffsetDateTime.now())
                    .build();

            Page<AuditLog> logPage = new PageImpl<>(List.of(orderLog), PageRequest.of(0, 10), 1);
            when(auditLogRepository.findByEntityType(eq("ORDER"), any(PageRequest.class))).thenReturn(logPage);

            PageResponse<AuditLogResponse> result = auditService.getByEntityType("ORDER", 0, 10);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getEntityType()).isEqualTo("ORDER");
        }
    }

    // =========================================================
    // getByAction() Tests
    // =========================================================
    @Nested
    @DisplayName("getByAction() Tests")
    class GetByActionTests {

        @Test
        @DisplayName("Should get audit logs by action")
        void shouldGetAuditLogsByAction() {
            Page<AuditLog> logPage = new PageImpl<>(List.of(auditLog), PageRequest.of(0, 10), 1);
            when(auditLogRepository.findByAction(eq("UPDATE"), any(PageRequest.class))).thenReturn(logPage);

            PageResponse<AuditLogResponse> result = auditService.getByAction("UPDATE", 0, 10);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getAction()).isEqualTo("UPDATE");
        }

        @Test
        @DisplayName("Should filter by CREATE action")
        void shouldFilterByCreateAction() {
            AuditLog createLog = AuditLog.builder()
                    .id(UUID.randomUUID())
                    .actor(user)
                    .actorEmail("manager@osms.vn")
                    .action("CREATE")
                    .entityType("INVENTORY")
                    .entityId(UUID.randomUUID())
                    .entityName("SKU-002")
                    .performedAt(OffsetDateTime.now())
                    .build();

            Page<AuditLog> logPage = new PageImpl<>(List.of(createLog), PageRequest.of(0, 10), 1);
            when(auditLogRepository.findByAction(eq("CREATE"), any(PageRequest.class))).thenReturn(logPage);

            PageResponse<AuditLogResponse> result = auditService.getByAction("CREATE", 0, 10);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getAction()).isEqualTo("CREATE");
        }

        @Test
        @DisplayName("Should filter by DELETE action")
        void shouldFilterByDeleteAction() {
            AuditLog deleteLog = AuditLog.builder()
                    .id(UUID.randomUUID())
                    .actor(user)
                    .actorEmail("manager@osms.vn")
                    .action("DELETE")
                    .entityType("INVENTORY")
                    .entityId(UUID.randomUUID())
                    .entityName("SKU-003")
                    .performedAt(OffsetDateTime.now())
                    .build();

            Page<AuditLog> logPage = new PageImpl<>(List.of(deleteLog), PageRequest.of(0, 10), 1);
            when(auditLogRepository.findByAction(eq("DELETE"), any(PageRequest.class))).thenReturn(logPage);

            PageResponse<AuditLogResponse> result = auditService.getByAction("DELETE", 0, 10);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getAction()).isEqualTo("DELETE");
        }
    }

    // =========================================================
    // getByEntityTypeAndAction() Tests
    // =========================================================
    @Nested
    @DisplayName("getByEntityTypeAndAction() Tests")
    class GetByEntityTypeAndActionTests {

        @Test
        @DisplayName("Should get audit logs by entity type and action")
        void shouldGetAuditLogsByEntityTypeAndAction() {
            Page<AuditLog> logPage = new PageImpl<>(List.of(auditLog), PageRequest.of(0, 10), 1);
            when(auditLogRepository.findByEntityTypeAndAction(eq("INVENTORY"), eq("UPDATE"), any(PageRequest.class)))
                    .thenReturn(logPage);

            PageResponse<AuditLogResponse> result = auditService.getByEntityTypeAndAction("INVENTORY", "UPDATE", 0, 10);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getEntityType()).isEqualTo("INVENTORY");
            assertThat(result.getContent().get(0).getAction()).isEqualTo("UPDATE");
        }

        @Test
        @DisplayName("Should return empty when no matching logs")
        void shouldReturnEmptyWhenNoMatchingLogs() {
            Page<AuditLog> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
            when(auditLogRepository.findByEntityTypeAndAction(anyString(), anyString(), any(PageRequest.class)))
                    .thenReturn(emptyPage);

            PageResponse<AuditLogResponse> result = auditService.getByEntityTypeAndAction("INVENTORY", "DELETE", 0, 10);

            assertThat(result.getContent()).isEmpty();
        }
    }

    // =========================================================
    // searchOrderLogs() Tests
    // =========================================================
    @Nested
    @DisplayName("searchOrderLogs() Tests")
    class SearchOrderLogsTests {

        @Test
        @DisplayName("Should search logs with keyword")
        void shouldSearchLogsWithKeyword() {
            Page<AuditLog> logPage = new PageImpl<>(List.of(auditLog), PageRequest.of(0, 10), 1);
            when(auditLogRepository.searchOrderLogs(any(), any(), any(), any(), any()))
                    .thenReturn(logPage);

            PageResponse<AuditLogResponse> result = auditService.searchOrderLogs("SKU-001", null, null, null, 0, 10);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("Should search logs with action filter")
        void shouldSearchLogsWithActionFilter() {
            Page<AuditLog> logPage = new PageImpl<>(List.of(auditLog), PageRequest.of(0, 10), 1);
            when(auditLogRepository.searchOrderLogs(any(), any(), any(), any(), any()))
                    .thenReturn(logPage);

            PageResponse<AuditLogResponse> result = auditService.searchOrderLogs("SKU", null, null, "UPDATE", 0, 10);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("Should search logs with date range")
        void shouldSearchLogsWithDateRange() {
            OffsetDateTime from = OffsetDateTime.now().minusDays(7);
            OffsetDateTime to = OffsetDateTime.now();

            Page<AuditLog> logPage = new PageImpl<>(List.of(auditLog), PageRequest.of(0, 10), 1);
            when(auditLogRepository.searchOrderLogs(any(), any(), any(), any(), any()))
                    .thenReturn(logPage);

            PageResponse<AuditLogResponse> result = auditService.searchOrderLogs("SKU", from, to, null, 0, 10);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("Should return empty when search has no results")
        void shouldReturnEmptyWhenSearchHasNoResults() {
            Page<AuditLog> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
            when(auditLogRepository.searchOrderLogs(any(), any(), any(), any(), any()))
                    .thenReturn(emptyPage);

            PageResponse<AuditLogResponse> result = auditService.searchOrderLogs("NONEXISTENT", null, null, null, 0, 10);

            assertThat(result.getContent()).isEmpty();
        }
    }

    // =========================================================
    // record() Tests
    // =========================================================
    @Nested
    @DisplayName("record() Tests")
    class RecordTests {

        @Test
        @DisplayName("Should record audit log successfully with changes")
        void shouldRecordAuditLogSuccessfullyWithChanges() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Map<String, Object> changes = new HashMap<>();
            changes.put("quantity", 100);

            auditService.record(userId, "manager@osms.vn", "UPDATE", "INVENTORY", entityId, "SKU-001", changes);

            verify(auditLogRepository).save(argThat(log ->
                    log.getActorEmail().equals("manager@osms.vn") &&
                            log.getAction().equals("UPDATE") &&
                            log.getEntityType().equals("INVENTORY") &&
                            log.getEntityId().equals(entityId) &&
                            log.getChanges() != null
            ));
        }

        @Test
        @DisplayName("Should record audit log without changes")
        void shouldRecordAuditLogWithoutChanges() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

            auditService.record(userId, "manager@osms.vn", "CREATE", "ORDER", entityId, "ORD-001", null);

            verify(auditLogRepository).save(argThat(log ->
                    log.getActorEmail().equals("manager@osms.vn") &&
                            log.getAction().equals("CREATE") &&
                            log.getEntityType().equals("ORDER") &&
                            log.getChanges() == null
            ));
        }

        @Test
        @DisplayName("Should record audit log when user not found")
        void shouldRecordAuditLogWhenUserNotFound() {
            when(userRepository.findById(userId)).thenReturn(Optional.empty());
            when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

            auditService.record(userId, "unknown@osms.vn", "DELETE", "INVENTORY", entityId, "SKU-001", null);

            verify(auditLogRepository).save(argThat(log ->
                    log.getActor() == null &&
                            log.getActorEmail().equals("unknown@osms.vn")
            ));
        }

        @Test
        @DisplayName("Should set performedAt timestamp")
        void shouldSetPerformedAtTimestamp() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

            OffsetDateTime before = OffsetDateTime.now();
            auditService.record(userId, "manager@osms.vn", "UPDATE", "INVENTORY", entityId, "SKU-001", null);
            OffsetDateTime after = OffsetDateTime.now();

            verify(auditLogRepository).save(argThat(log ->
                    log.getPerformedAt().isAfter(before.minusSeconds(1)) &&
                            log.getPerformedAt().isBefore(after.plusSeconds(1))
            ));
        }

        @Test
        @DisplayName("Should convert changes object to map")
        void shouldConvertChangesObjectToMap() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Map<String, Object> expectedChanges = new HashMap<>();
            expectedChanges.put("status", "ACTIVE");

            auditService.record(userId, "manager@osms.vn", "UPDATE", "INVENTORY", entityId, "SKU-001", expectedChanges);

            verify(auditLogRepository).save(argThat(log ->
                    log.getChanges() != null &&
                            log.getChanges().containsKey("status")
            ));
        }
    }

    // =========================================================
    // Pagination Tests
    // =========================================================
    @Nested
    @DisplayName("Pagination Tests")
    class PaginationTests {

        @Test
        @DisplayName("Should return correct page info")
        void shouldReturnCorrectPageInfo() {
            List<AuditLog> logs = Arrays.asList(
                    AuditLog.builder().id(UUID.randomUUID()).action("CREATE").entityType("A").build(),
                    AuditLog.builder().id(UUID.randomUUID()).action("UPDATE").entityType("B").build()
            );
            Page<AuditLog> logPage = new PageImpl<>(logs, PageRequest.of(0, 2), 100);

            when(auditLogRepository.findAll(any(PageRequest.class))).thenReturn(logPage);

            PageResponse<AuditLogResponse> result = auditService.getAll(0, 2);

            assertThat(result.getPage()).isEqualTo(0);
            assertThat(result.getSize()).isEqualTo(2);
            assertThat(result.getTotalElements()).isEqualTo(100);
            assertThat(result.getTotalPages()).isEqualTo(50);
            assertThat(result.isFirst()).isTrue();
            assertThat(result.isLast()).isFalse();
        }

        @Test
        @DisplayName("Should handle last page correctly")
        void shouldHandleLastPageCorrectly() {
            List<AuditLog> logs = Arrays.asList(
                    AuditLog.builder().id(UUID.randomUUID()).action("CREATE").entityType("A").build()
            );
            Page<AuditLog> logPage = new PageImpl<>(logs, PageRequest.of(49, 2), 100);

            when(auditLogRepository.findAll(any(PageRequest.class))).thenReturn(logPage);

            PageResponse<AuditLogResponse> result = auditService.getAll(49, 2);

            assertThat(result.getPage()).isEqualTo(49);
            assertThat(result.isLast()).isTrue();
            assertThat(result.isFirst()).isFalse();
        }
    }

    // =========================================================
    // Edge Cases
    // =========================================================
    @Nested
    @DisplayName("Edge Cases")
    class EdgeCasesTests {

        @Test
        @DisplayName("Should handle null actor gracefully")
        void shouldHandleNullActorGracefully() {
            AuditLog logWithoutActor = AuditLog.builder()
                    .id(UUID.randomUUID())
                    .actor(null)
                    .actorEmail("system@osms.vn")
                    .action("SYSTEM")
                    .entityType("INVENTORY")
                    .entityId(entityId)
                    .entityName("Auto-process")
                    .performedAt(OffsetDateTime.now())
                    .build();

            Page<AuditLog> logPage = new PageImpl<>(List.of(logWithoutActor), PageRequest.of(0, 10), 1);
            when(auditLogRepository.findAll(any(PageRequest.class))).thenReturn(logPage);

            PageResponse<AuditLogResponse> result = auditService.getAll(0, 10);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getActorId()).isNull();
            assertThat(result.getContent().get(0).getActorEmail()).isEqualTo("system@osms.vn");
        }

        @Test
        @DisplayName("Should handle null changes gracefully")
        void shouldHandleNullChangesGracefully() {
            AuditLog logWithoutChanges = AuditLog.builder()
                    .id(UUID.randomUUID())
                    .actor(user)
                    .actorEmail("manager@osms.vn")
                    .action("CREATE")
                    .entityType("INVENTORY")
                    .entityId(entityId)
                    .entityName("SKU-001")
                    .changes(null)
                    .performedAt(OffsetDateTime.now())
                    .build();

            Page<AuditLog> logPage = new PageImpl<>(List.of(logWithoutChanges), PageRequest.of(0, 10), 1);
            when(auditLogRepository.findAll(any(PageRequest.class))).thenReturn(logPage);

            PageResponse<AuditLogResponse> result = auditService.getAll(0, 10);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getChanges()).isNull();
        }

        @Test
        @DisplayName("Should handle empty entity name")
        void shouldHandleEmptyEntityName() {
            AuditLog logWithEmptyName = AuditLog.builder()
                    .id(UUID.randomUUID())
                    .actor(user)
                    .actorEmail("manager@osms.vn")
                    .action("DELETE")
                    .entityType("INVENTORY")
                    .entityId(entityId)
                    .entityName("")
                    .performedAt(OffsetDateTime.now())
                    .build();

            Page<AuditLog> logPage = new PageImpl<>(List.of(logWithEmptyName), PageRequest.of(0, 10), 1);
            when(auditLogRepository.findAll(any(PageRequest.class))).thenReturn(logPage);

            PageResponse<AuditLogResponse> result = auditService.getAll(0, 10);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getEntityName()).isEmpty();
        }
    }
}
