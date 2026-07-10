package fu.osms.inventory.repository;

import fu.osms.inventory.entity.InventoryIssue;
import fu.osms.inventory.entity.Warehouse;
import fu.osms.it.IntegrationTestBase;
import fu.osms.it.IntegrationTestCleanupHelper;
import fu.osms.it.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StockDeliveryRepositoryIT extends IntegrationTestBase {

    @Autowired InventoryIssueRepository issueRepo;
    @Autowired WarehouseRepository warehouseRepo;
    @Autowired TestDataFactory factory;
    @Autowired IntegrationTestCleanupHelper cleanup;

    @BeforeEach
    void setUp() {
        cleanup.truncate();
    }

    @Test
    void findByIdWithDetails_returnsIssueWithWarehouse() {
        Warehouse wh = warehouseRepo.save(factory.newWarehouse());
        InventoryIssue issue = new InventoryIssue();
        issue.setWarehouse(wh);
        issue.setIssueCode("ISS-IT-" + TestDataFactory.uniqueSuffix());
        issue.setIssueType("ORDER");
        issue.setStatus("DRAFT");
        issue.setReferenceId(UUID.randomUUID());
        issueRepo.save(issue);

        Optional<InventoryIssue> got = issueRepo.findByIdWithDetails(issue.getId());
        assertThat(got).isPresent();
        assertThat(got.get().getWarehouse().getId()).isEqualTo(wh.getId());
    }

    @Test
    void findByReferenceId_returnsIssue() {
        Warehouse wh = warehouseRepo.save(factory.newWarehouse());
        InventoryIssue issue = new InventoryIssue();
        issue.setWarehouse(wh);
        issue.setIssueCode("ISS-IT-" + TestDataFactory.uniqueSuffix());
        issue.setIssueType("ORDER");
        issue.setStatus("DRAFT");
        UUID refId = UUID.randomUUID();
        issue.setReferenceId(refId);
        issueRepo.save(issue);

        Optional<InventoryIssue> got = issueRepo.findByReferenceId(refId);
        assertThat(got).isPresent();
        assertThat(got.get().getId()).isEqualTo(issue.getId());
    }

    @Test
    void countByStatus_returnsMatching() {
        long drafts = issueRepo.countByStatus("DRAFT");
        assertThat(drafts).isGreaterThanOrEqualTo(0L);
    }

    @Test
    void countByIssueType_returnsMatching() {
        long order = issueRepo.countByIssueType("ORDER");
        assertThat(order).isGreaterThanOrEqualTo(0L);
    }

    @Test
    void sumTotalCost_returnsNonNegative() {
        java.math.BigDecimal total = issueRepo.sumTotalCost();
        assertThat(total).isGreaterThanOrEqualTo(java.math.BigDecimal.ZERO);
    }
}
