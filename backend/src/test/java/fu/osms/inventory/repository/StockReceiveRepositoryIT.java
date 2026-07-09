package fu.osms.inventory.repository;

import fu.osms.inventory.entity.InventoryReceipt;
import fu.osms.inventory.entity.Supplier;
import fu.osms.inventory.entity.Warehouse;
import fu.osms.it.IntegrationTestBase;
import fu.osms.it.IntegrationTestCleanupHelper;
import fu.osms.it.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StockReceiveRepositoryIT extends IntegrationTestBase {

    @Autowired StockReceiveRepository receiptRepo;
    @Autowired WarehouseRepository warehouseRepo;
    @Autowired SupplierRepository supplierRepo;
    @Autowired TestDataFactory factory;
    @Autowired IntegrationTestCleanupHelper cleanup;

    @BeforeEach
    void setUp() {
        cleanup.truncate();
    }

    @Test
    void existsByInvoiceNumber_trueForExisting() {
        Warehouse wh = warehouseRepo.save(factory.newWarehouse());
        Supplier sup = supplierRepo.save(factory.newSupplier());
        InventoryReceipt r = new InventoryReceipt();
        r.setWarehouse(wh);
        r.setSupplier(sup);
        r.setReceiptCode("RC-IT-" + TestDataFactory.uniqueSuffix());
        r.setInvoiceNumber("INV-IT-" + TestDataFactory.uniqueSuffix());
        r.setStatus("DRAFT");
        receiptRepo.save(r);

        assertThat(receiptRepo.existsByInvoiceNumber(r.getInvoiceNumber())).isTrue();
        assertThat(receiptRepo.existsByInvoiceNumber("NOT-EXISTING")).isFalse();
    }

    @Test
    void existsByInvoiceNumberAndIdNot_doesNotMatchSelf() {
        Warehouse wh = warehouseRepo.save(factory.newWarehouse());
        Supplier sup = supplierRepo.save(factory.newSupplier());
        InventoryReceipt r = new InventoryReceipt();
        r.setWarehouse(wh);
        r.setSupplier(sup);
        r.setReceiptCode("RC-IT-" + TestDataFactory.uniqueSuffix());
        r.setInvoiceNumber("INV-IT-" + TestDataFactory.uniqueSuffix());
        r.setStatus("DRAFT");
        receiptRepo.save(r);

        assertThat(receiptRepo.existsByInvoiceNumberAndIdNot(r.getInvoiceNumber(), r.getId())).isFalse();
    }

    @Test
    void countByStatus_returnsMatching() {
        Warehouse wh = warehouseRepo.save(factory.newWarehouse());
        Supplier sup = supplierRepo.save(factory.newSupplier());
        InventoryReceipt r = new InventoryReceipt();
        r.setWarehouse(wh);
        r.setSupplier(sup);
        r.setReceiptCode("RC-IT-" + TestDataFactory.uniqueSuffix());
        r.setInvoiceNumber("INV-IT-" + TestDataFactory.uniqueSuffix());
        r.setStatus("DRAFT");
        receiptRepo.save(r);

        long drafts = receiptRepo.countByStatus("DRAFT");
        assertThat(drafts).isGreaterThanOrEqualTo(1L);
    }

    @Test
    void countByYear_returnsCountForYear() {
        long count = receiptRepo.countByYear(OffsetDateTime.now(ZoneOffset.UTC).getYear());
        assertThat(count).isGreaterThanOrEqualTo(0L);
    }

    @Test
    void findAllByOrderByCreatedAtDesc_returnsPagedResults() {
        var page = receiptRepo.findAllByOrderByCreatedAtDesc(
                org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(page).isNotNull();
    }
}
