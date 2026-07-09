package fu.osms.sync.repository;

import fu.osms.it.IntegrationTestBase;
import fu.osms.it.IntegrationTestCleanupHelper;
import fu.osms.it.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookEventRepositoryIT extends IntegrationTestBase {

    @Autowired WebhookEventRepository webhookRepo;
    @Autowired IntegrationTestCleanupHelper cleanup;

    @BeforeEach
    void setUp() {
        cleanup.truncate();
    }

    @Test
    void findByChannelId_returnsPaged() {
        var page = webhookRepo.findByChannel_Id(UUID.randomUUID(), PageRequest.of(0, 10));
        assertThat(page).isNotNull();
    }

    @Test
    void findByChannelIdAndStatus_returnsPaged() {
        var page = webhookRepo.findByChannel_IdAndStatus(UUID.randomUUID(), "PENDING", PageRequest.of(0, 10));
        assertThat(page).isNotNull();
    }

    @Test
    void findByPlatformAndExternalEventId_returnsEmptyForUnknown() {
        var got = webhookRepo.findByPlatformAndExternalEventId(
                fu.osms.common.enums.PlatformType.SHOPIFY,
                "IT-NONEXISTENT-" + TestDataFactory.uniqueSuffix());
        assertThat(got).isEmpty();
    }
}
