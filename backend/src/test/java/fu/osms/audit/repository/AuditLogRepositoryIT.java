package fu.osms.audit.repository;

import fu.osms.audit.entity.AuditLog;
import fu.osms.it.IntegrationTestBase;
import fu.osms.it.IntegrationTestCleanupHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuditLogRepositoryIT extends IntegrationTestBase {

    @Autowired AuditLogRepository auditRepo;
    @Autowired IntegrationTestCleanupHelper cleanup;

    @BeforeEach
    void setUp() {
        cleanup.truncate();
    }

    @Test
    void findByEntityTypeAndEntityId_returnsPaged() {
        var page = auditRepo.findByEntityTypeAndEntityId("CUSTOMER", UUID.randomUUID(), PageRequest.of(0, 10));
        assertThat(page).isNotNull();
    }

    @Test
    void findByActorId_returnsPaged() {
        var page = auditRepo.findByActorId(UUID.randomUUID(), PageRequest.of(0, 10));
        assertThat(page).isNotNull();
    }

    @Test
    void findByPerformedAtBetween_returnsPaged() {
        var from = java.time.OffsetDateTime.now().minusDays(1);
        var to = java.time.OffsetDateTime.now().plusDays(1);
        var page = auditRepo.findByPerformedAtBetween(from, to, PageRequest.of(0, 10));
        assertThat(page).isNotNull();
    }

    @Test
    void findByEntityType_returnsPaged() {
        var page = auditRepo.findByEntityType("ORDER", PageRequest.of(0, 10));
        assertThat(page).isNotNull();
    }

    @Test
    void findByAction_returnsPaged() {
        var page = auditRepo.findByAction("CREATE", PageRequest.of(0, 10));
        assertThat(page).isNotNull();
    }
}
