package fu.osms.inventory.repository;

import fu.osms.inventory.entity.Supplier;
import fu.osms.it.IntegrationTestBase;
import fu.osms.it.IntegrationTestCleanupHelper;
import fu.osms.it.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SupplierRepositoryIT extends IntegrationTestBase {

    @Autowired SupplierRepository supplierRepo;
    @Autowired TestDataFactory factory;
    @Autowired IntegrationTestCleanupHelper cleanup;

    @BeforeEach
    void setUp() {
        cleanup.truncate();
    }

    @Test
    void existsByNameIgnoreCase_caseInsensitive() {
        Supplier s = supplierRepo.save(factory.newSupplier());

        assertThat(supplierRepo.existsByNameIgnoreCase(s.getName().toLowerCase())).isTrue();
        assertThat(supplierRepo.existsByNameIgnoreCase("NONEXISTENT-SUPPLIER")).isFalse();
    }

    @Test
    void existsByNameIgnoreCaseAndIdNot_falseForSelf() {
        Supplier s = supplierRepo.save(factory.newSupplier());

        assertThat(supplierRepo.existsByNameIgnoreCaseAndIdNot(s.getName(), s.getId())).isFalse();
    }

    @Test
    void findByIdAndIsActiveTrue_returnsActive() {
        Supplier s = supplierRepo.save(factory.newSupplier());

        Optional<Supplier> got = supplierRepo.findByIdAndIsActiveTrue(s.getId());
        assertThat(got).isPresent();
    }

    @Test
    void findLatestCode_returnsCode() {
        supplierRepo.save(factory.newSupplier());

        String code = supplierRepo.findLatestCode();
        assertThat(code).isNotNull();
        assertThat(code).startsWith("SUP");
    }

    @Test
    void findTopByOrderBySupplierCodeDesc_returnsLatest() {
        supplierRepo.save(factory.newSupplier());

        Optional<Supplier> top = supplierRepo.findTopByOrderBySupplierCodeDesc();
        assertThat(top).isPresent();
    }

    @Test
    void findByIsActiveTrueOrderByNameAsc_returnsPaged() {
        var page = supplierRepo.findByIsActiveTrueOrderByNameAsc(PageRequest.of(0, 10));
        assertThat(page).isNotNull();
    }
}
