package fu.osms.inventory.repository;

import fu.osms.inventory.entity.Warehouse;
import fu.osms.it.IntegrationTestBase;
import fu.osms.it.IntegrationTestCleanupHelper;
import fu.osms.it.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class WarehouseRepositoryIT extends IntegrationTestBase {

    @Autowired WarehouseRepository warehouseRepo;
    @Autowired TestDataFactory factory;
    @Autowired IntegrationTestCleanupHelper cleanup;

    @BeforeEach
    void setUp() {
        cleanup.truncate();
    }

    @Test
    void findByIsActive_returnsActiveWarehouses() {
        Warehouse active = warehouseRepo.save(factory.newWarehouse());

        List<Warehouse> got = warehouseRepo.findByIsActive(true);
        assertThat(got).extracting(Warehouse::getId).contains(active.getId());
    }

    @Test
    void findByDeletedAtIsNull_excludesDeleted() {
        Warehouse wh = warehouseRepo.save(factory.newWarehouse());

        List<Warehouse> got = warehouseRepo.findByDeletedAtIsNull();
        assertThat(got).extracting(Warehouse::getId).contains(wh.getId());
    }

    @Test
    void findByIsActiveTrueOrderByNameAsc_returnsOrdered() {
        var list = warehouseRepo.findByIsActiveTrueOrderByNameAsc();
        assertThat(list).isNotNull();
    }

    @Test
    void findFirstByNameAndDeletedAtIsNull_returnsWarehouse() {
        Warehouse wh = warehouseRepo.save(factory.newWarehouse());

        Optional<Warehouse> got = warehouseRepo.findFirstByNameAndDeletedAtIsNullOrderByIdAsc(wh.getName());
        assertThat(got).isPresent();
    }
}
