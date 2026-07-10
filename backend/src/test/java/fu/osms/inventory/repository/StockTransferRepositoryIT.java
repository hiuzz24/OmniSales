package fu.osms.inventory.repository;

import fu.osms.inventory.entity.StockTransfer;
import fu.osms.inventory.entity.Warehouse;
import fu.osms.it.IntegrationTestBase;
import fu.osms.it.IntegrationTestCleanupHelper;
import fu.osms.it.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class StockTransferRepositoryIT extends IntegrationTestBase {

    @Autowired StockTransferRepository transferRepo;
    @Autowired WarehouseRepository warehouseRepo;
    @Autowired TestDataFactory factory;
    @Autowired IntegrationTestCleanupHelper cleanup;

    @BeforeEach
    void setUp() {
        cleanup.truncate();
    }

    @Test
    void searchTransfers_returnsByStatus() {
        Warehouse from = warehouseRepo.save(factory.newWarehouse());
        Warehouse to = warehouseRepo.save(factory.newWarehouse());

        StockTransfer t = new StockTransfer();
        t.setFromWarehouse(from);
        t.setToWarehouse(to);
        t.setTransferCode("TR-IT-" + TestDataFactory.uniqueSuffix());
        t.setStatus("DRAFT");
        transferRepo.save(t);

        var page = transferRepo.searchTransfers("DRAFT", null, null,
                org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(page.getContent()).extracting(StockTransfer::getId).contains(t.getId());
    }

    @Test
    void getTransferSummary_returnsNonNull() {
        var summary = transferRepo.getTransferSummary();
        assertThat(summary).isNotNull();
    }

    @Test
    void findLatestTransferCodeByPrefix_returnsCode() {
        Warehouse from = warehouseRepo.save(factory.newWarehouse());
        Warehouse to = warehouseRepo.save(factory.newWarehouse());

        StockTransfer t = new StockTransfer();
        t.setFromWarehouse(from);
        t.setToWarehouse(to);
        t.setTransferCode("TR-2025-IT-" + TestDataFactory.uniqueSuffix());
        t.setStatus("DRAFT");
        transferRepo.save(t);

        Optional<String> code = transferRepo.findLatestTransferCodeByPrefix("TR-2025-IT-%");
        assertThat(code).isPresent();
        assertThat(code.get()).startsWith("TR-2025-IT-");
    }

    @Test
    void findAll_returnsPagedTransfers() {
        var page = transferRepo.findAll(org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(page).isNotNull();
    }
}
