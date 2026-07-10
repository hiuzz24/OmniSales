package fu.osms.inventory.repository;

import fu.osms.it.IntegrationTestBase;
import fu.osms.it.IntegrationTestCleanupHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryTransactionRepositoryIT extends IntegrationTestBase {

    @Autowired InventoryTransactionRepository txRepo;
    @Autowired IntegrationTestCleanupHelper cleanup;

    @BeforeEach
    void setUp() {
        cleanup.truncate();
    }

    @Test
    void findByWarehouseId_returnsPaged() {
        var page = txRepo.findByWarehouseId(UUID.randomUUID(), PageRequest.of(0, 10));
        assertThat(page).isNotNull();
    }

    @Test
    void findByVariantId_returnsPaged() {
        var page = txRepo.findByVariantId(UUID.randomUUID(), PageRequest.of(0, 10));
        assertThat(page).isNotNull();
    }

    @Test
    void findAllWithDetails_returnsPaged() {
        var page = txRepo.findAllWithDetails(PageRequest.of(0, 10));
        assertThat(page).isNotNull();
    }

    @Test
    void findByVariantIdWithDetails_returnsPaged() {
        var page = txRepo.findByVariantIdWithDetails(UUID.randomUUID(), PageRequest.of(0, 10));
        assertThat(page).isNotNull();
    }

    @Test
    void findByPerformedAtBetween_returnsList() {
        var from = java.time.OffsetDateTime.now().minusDays(1);
        var to = java.time.OffsetDateTime.now().plusDays(1);
        var list = txRepo.findByPerformedAtBetween(from, to);
        assertThat(list).isNotNull();
    }
}
