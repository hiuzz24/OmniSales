package fu.osms.inventory.repository;

import fu.osms.inventory.entity.StocktakeSession;
import fu.osms.inventory.entity.Warehouse;
import fu.osms.it.IntegrationTestBase;
import fu.osms.it.IntegrationTestCleanupHelper;
import fu.osms.it.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class StocktakeRepositoryIT extends IntegrationTestBase {

    @Autowired StocktakeSessionRepository stocktakeRepo;
    @Autowired WarehouseRepository warehouseRepo;
    @Autowired TestDataFactory factory;
    @Autowired IntegrationTestCleanupHelper cleanup;

    @BeforeEach
    void setUp() {
        cleanup.truncate();
    }

    @Test
    void countByStatus_returnsMatching() {
        Warehouse wh = warehouseRepo.save(factory.newWarehouse());
        StocktakeSession s = new StocktakeSession();
        s.setWarehouse(wh);
        s.setSessionCode("STK-IT-" + TestDataFactory.uniqueSuffix());
        s.setStatus("DRAFT");
        stocktakeRepo.save(s);

        long drafts = stocktakeRepo.countByStatus("DRAFT");
        assertThat(drafts).isGreaterThanOrEqualTo(1L);
    }

    @Test
    void countByCreatedYear_returnsNonNegative() {
        long count = stocktakeRepo.countByCreatedYear(java.time.OffsetDateTime.now().getYear());
        assertThat(count).isGreaterThanOrEqualTo(0L);
    }

    @Test
    void findAll_returnsPaged() {
        var page = stocktakeRepo.findAll(org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(page).isNotNull();
    }
}
