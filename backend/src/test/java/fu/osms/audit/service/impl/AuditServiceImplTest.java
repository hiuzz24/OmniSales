package fu.osms.audit.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.audit.entity.AuditLog;
import fu.osms.audit.repository.AuditLogRepository;
import fu.osms.auth.entity.User;
import fu.osms.auth.repository.UserRepository;
import fu.osms.common.dto.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditServiceImpl Tests")
class AuditServiceImplTest {

    @Mock private AuditLogRepository auditLogRepository;
    @Mock private UserRepository userRepository;

    private AuditServiceImpl service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new AuditServiceImpl(auditLogRepository, userRepository, objectMapper);
    }

    private AuditLog logEntity(UUID id, String action, String entityType) {
        return AuditLog.builder()
                .id(id)
                .actorEmail("actor@example.com")
                .action(action)
                .entityType(entityType)
                .entityId(UUID.randomUUID())
                .entityName("Test " + entityType)
                .performedAt(OffsetDateTime.now())
                .build();
    }

    @Test
    @DisplayName("getAll: returns page of logs sorted by performedAt DESC")
    void getAll() {
        AuditLog log = logEntity(UUID.randomUUID(), "CREATE", "ORDER");
        Page<AuditLog> page = new PageImpl<>(List.of(log));
        when(auditLogRepository.findAll(any(Pageable.class))).thenReturn(page);

        PageResponse<?> result = service.getAll(0, 10);

        assertThat(result.getContent()).hasSize(1);
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(auditLogRepository).findAll(captor.capture());
        Sort.Order order = captor.getValue().getSort().getOrderFor("performedAt");
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @DisplayName("getByEntity: filters by entityType + entityId")
    void getByEntity() {
        UUID entityId = UUID.randomUUID();
        AuditLog log = logEntity(UUID.randomUUID(), "CREATE", "ORDER");
        Page<AuditLog> page = new PageImpl<>(List.of(log));
        when(auditLogRepository.findByEntityTypeAndEntityId(eq("ORDER"), eq(entityId), any(Pageable.class)))
                .thenReturn(page);

        PageResponse<?> result = service.getByEntity("ORDER", entityId, 0, 10);

        assertThat(result.getContent()).hasSize(1);
        verify(auditLogRepository).findByEntityTypeAndEntityId(
                eq("ORDER"), eq(entityId),
                eq(PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "performedAt"))));
    }

    @Test
    @DisplayName("getByActor: filters by actorId")
    void getByActor() {
        UUID actorId = UUID.randomUUID();
        AuditLog log = logEntity(UUID.randomUUID(), "UPDATE", "PRODUCT");
        Page<AuditLog> page = new PageImpl<>(List.of(log));
        when(auditLogRepository.findByActorId(eq(actorId), any(Pageable.class))).thenReturn(page);

        PageResponse<?> result = service.getByActor(actorId, 0, 10);

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("getByDateRange: filters by date range")
    void getByDateRange() {
        OffsetDateTime from = OffsetDateTime.now().minusDays(7);
        OffsetDateTime to = OffsetDateTime.now();
        AuditLog log = logEntity(UUID.randomUUID(), "DELETE", "CHANNEL");
        Page<AuditLog> page = new PageImpl<>(List.of(log));
        when(auditLogRepository.findByPerformedAtBetween(eq(from), eq(to), any(Pageable.class))).thenReturn(page);

        PageResponse<?> result = service.getByDateRange(from, to, 0, 10);

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("getByAction: filters by action")
    void getByAction() {
        AuditLog log = logEntity(UUID.randomUUID(), "CREATE", "ORDER");
        Page<AuditLog> page = new PageImpl<>(List.of(log));
        when(auditLogRepository.findByAction(eq("CREATE"), any(Pageable.class))).thenReturn(page);

        PageResponse<?> result = service.getByAction("CREATE", 0, 10);

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("record: persists a new audit log with actor (if found) and converted changes map")
    void record_persists() {
        UUID actorId = UUID.randomUUID();
        User actor = User.builder().id(actorId).email("actor@example.com").build();
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));

        service.record(actorId, "actor@example.com", "CREATE", "ORDER",
                UUID.randomUUID(), "Order #1", Map.of("status", "PAID"));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getAction()).isEqualTo("CREATE");
        assertThat(saved.getActor()).isEqualTo(actor);
        assertThat(saved.getChanges()).containsEntry("status", "PAID");
        assertThat(saved.getPerformedAt()).isNotNull();
    }

    @Test
    @DisplayName("record: actor remains null when userRepository cannot find the actor")
    void record_actorNotFound() {
        UUID actorId = UUID.randomUUID();
        when(userRepository.findById(actorId)).thenReturn(Optional.empty());

        service.record(actorId, "missing@example.com", "DELETE", "PRODUCT",
                UUID.randomUUID(), "Product A", null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getActor()).isNull();
        assertThat(captor.getValue().getChanges()).isNull();
    }
}
