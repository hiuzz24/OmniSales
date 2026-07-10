package fu.osms.notification.repository;

import fu.osms.it.IntegrationTestBase;
import fu.osms.it.IntegrationTestCleanupHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationRepositoryIT extends IntegrationTestBase {

    @Autowired NotificationRepository notificationRepo;
    @Autowired IntegrationTestCleanupHelper cleanup;

    @BeforeEach
    void setUp() {
        cleanup.truncate();
    }

    @Test
    void findByUserIdOrderByCreatedAtDesc_returnsPaged() {
        var page = notificationRepo.findByUserIdOrderByCreatedAtDesc(UUID.randomUUID(), PageRequest.of(0, 10));
        assertThat(page).isNotNull();
    }

    @Test
    void findByUserIdAndReadAtIsNullOrderByCreatedAtDesc_returnsPaged() {
        var page = notificationRepo.findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(UUID.randomUUID(), PageRequest.of(0, 10));
        assertThat(page).isNotNull();
    }

    @Test
    void findAllByOrderByCreatedAtDesc_returnsPaged() {
        var page = notificationRepo.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 10));
        assertThat(page).isNotNull();
    }

    @Test
    void countByUserIdAndReadAtIsNull_returnsZeroOrMore() {
        long c = notificationRepo.countByUserIdAndReadAtIsNull(UUID.randomUUID());
        assertThat(c).isGreaterThanOrEqualTo(0L);
    }

    @Test
    void countByReadAtIsNull_returnsZeroOrMore() {
        long c = notificationRepo.countByReadAtIsNull();
        assertThat(c).isGreaterThanOrEqualTo(0L);
    }
}
