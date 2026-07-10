package fu.osms.inventory.repository;

import fu.osms.catalog.entity.Product;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.catalog.repository.CategoryRepository;
import fu.osms.catalog.repository.ProductRepository;
import fu.osms.catalog.repository.ProductVariantRepository;
import fu.osms.inventory.entity.InventoryItem;
import fu.osms.inventory.entity.Warehouse;
import fu.osms.it.IntegrationTestBase;
import fu.osms.it.IntegrationTestCleanupHelper;
import fu.osms.it.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class InventoryItemRepositoryIT extends IntegrationTestBase {

    @Autowired InventoryItemRepository invRepo;
    @Autowired WarehouseRepository warehouseRepo;
    @Autowired CategoryRepository categoryRepo;
    @Autowired ProductRepository productRepo;
    @Autowired ProductVariantRepository variantRepo;
    @Autowired TestDataFactory factory;
    @Autowired IntegrationTestCleanupHelper cleanup;

    @BeforeEach
    void setUp() {
        cleanup.truncate();
    }

    private Warehouse newWarehouse() {
        return warehouseRepo.save(factory.newWarehouse());
    }

    private ProductVariant newVariant() {
        var category = categoryRepo.save(factory.newCategory());
        var product = productRepo.save(factory.newProduct(category.getId()));
        return variantRepo.save(factory.newVariant(product.getId()));
    }

    @Test
    void findByWarehouseId_returnsItemsOfWarehouse() {
        Warehouse wh = newWarehouse();
        ProductVariant v = newVariant();
        InventoryItem item = invRepo.save(factory.newInventoryItem(wh.getId(), v.getId(), 10));

        List<InventoryItem> got = invRepo.findByWarehouseId(wh.getId());
        assertThat(got).extracting(InventoryItem::getId).contains(item.getId());
    }

    @Test
    void findByWarehouseIdAndVariantId_uniqueForPair() {
        Warehouse wh = newWarehouse();
        ProductVariant v = newVariant();
        InventoryItem item = invRepo.save(factory.newInventoryItem(wh.getId(), v.getId(), 7));

        var got = invRepo.findByWarehouseIdAndVariantId(wh.getId(), v.getId());
        assertThat(got).isPresent();
        assertThat(got.get().getId()).isEqualTo(item.getId());
    }

    @Test
    void findByVariantIdIn_returnsItemsForAnyVariant() {
        Warehouse wh = newWarehouse();
        ProductVariant v1 = newVariant();
        ProductVariant v2 = newVariant();
        InventoryItem i1 = invRepo.save(factory.newInventoryItem(wh.getId(), v1.getId(), 1));
        InventoryItem i2 = invRepo.save(factory.newInventoryItem(wh.getId(), v2.getId(), 2));

        List<InventoryItem> got = invRepo.findByVariantIdIn(List.of(v1.getId(), v2.getId()));
        assertThat(got).extracting(InventoryItem::getId).contains(i1.getId(), i2.getId());
    }

    @Test
    void findLowStockItems_includesOnlyLowStock() {
        Warehouse wh = newWarehouse();
        ProductVariant vLow = newVariant();
        ProductVariant vOk = newVariant();
        InventoryItem low = invRepo.save(factory.newInventoryItem(wh.getId(), vLow.getId(), 1));
        InventoryItem ok = invRepo.save(factory.newInventoryItem(wh.getId(), vOk.getId(), 100));
        ok.setLowStockThreshold(50);
        ok.setAvailableQuantity(80);
        invRepo.save(ok);

        List<InventoryItem> lowList = invRepo.findLowStockItems();
        assertThat(lowList).extracting(InventoryItem::getId).contains(low.getId());
    }

    @Test
    void findByWarehouseId_paginationWorks() {
        Warehouse wh = newWarehouse();
        ProductVariant v1 = newVariant();
        ProductVariant v2 = newVariant();
        invRepo.save(factory.newInventoryItem(wh.getId(), v1.getId(), 1));
        invRepo.save(factory.newInventoryItem(wh.getId(), v2.getId(), 2));

        var page = invRepo.findByWarehouseId(wh.getId(), org.springframework.data.domain.PageRequest.of(0, 1));
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(2);
    }
}
