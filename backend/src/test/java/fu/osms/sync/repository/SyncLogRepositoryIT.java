package fu.osms.sync.repository;

import fu.osms.channel.entity.Channel;
import fu.osms.common.enums.PlatformType;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.common.enums.SyncStatus;
import fu.osms.it.IntegrationTestBase;
import fu.osms.it.IntegrationTestCleanupHelper;
import fu.osms.it.TestDataFactory;
import fu.osms.sync.entity.SyncLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

class SyncLogRepositoryIT extends IntegrationTestBase {

    @Autowired SyncLogRepository syncLogRepo;
    @Autowired ChannelRepository channelRepo;
    @Autowired TestDataFactory factory;
    @Autowired IntegrationTestCleanupHelper cleanup;

    @BeforeEach
    void setUp() {
        cleanup.truncate();
    }

    @Test
    void findByStatus_returnsPaged() {
        var page = syncLogRepo.findByStatus(SyncStatus.SYNCED, PageRequest.of(0, 10));
        assertThat(page).isNotNull();
    }

    @Test
    void findByIdempotencyKey_returnsNullForUnknown() {
        var got = syncLogRepo.findByIdempotencyKey("IT-NONEXISTENT-" + TestDataFactory.uniqueSuffix());
        assertThat(got).isEmpty();
    }

    @Test
    void findAllByOrderByStartedAtDesc_returnsPaged() {
        var page = syncLogRepo.findAllByOrderByStartedAtDesc(PageRequest.of(0, 10));
        assertThat(page).isNotNull();
    }
}
