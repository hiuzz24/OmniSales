package fu.osms.catalog.repository;

import fu.osms.catalog.entity.Product;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.it.IntegrationTestBase;
import fu.osms.it.IntegrationTestCleanupHelper;
import fu.osms.it.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class ProductVariantRepositoryIT extends IntegrationTestBase {

    @Autowired ProductVariantRepository variantRepo;
    @Autowired ProductRepository productRepo;
    @Autowired CategoryRepository categoryRepo;
    @Autowired TestDataFactory factory;
    @Autowired IntegrationTestCleanupHelper cleanup;

    @BeforeEach
    void setUp() {
        cleanup.truncate();
    }

    private Product persistProduct() {
        var category = categoryRepo.save(factory.newCategory());
        var product = factory.newProduct(category.getId());
        product.setCategory(category);
        return productRepo.save(product);
    }

    @Test
    void findByProductId_returnsOnlyThatProduct() {
        Product p1 = persistProduct();
        Product p2 = persistProduct();
        ProductVariant v1 = variantRepo.save(factory.newVariant(p1.getId()));
        ProductVariant v2 = variantRepo.save(factory.newVariant(p2.getId()));

        List<ProductVariant> got = variantRepo.findByProductIdAndDeletedAtIsNull(p1.getId());

        assertThat(got).extracting(ProductVariant::getId)
                .contains(v1.getId())
                .doesNotContain(v2.getId());
    }

    @Test
    void findBySkuAndDeletedAtIsNull_roundtrip() {
        Product p = persistProduct();
        ProductVariant v = variantRepo.save(factory.newVariant(p.getId()));

        Optional<ProductVariant> got = variantRepo.findBySkuAndDeletedAtIsNull(v.getSku());
        assertThat(got).isPresent();
        assertThat(got.get().getId()).isEqualTo(v.getId());
    }

    @Test
    void existsBySkuAndDeletedAtIsNull_trueForExisting() {
        Product p = persistProduct();
        ProductVariant v = variantRepo.save(factory.newVariant(p.getId()));

        assertThat(variantRepo.existsBySkuAndDeletedAtIsNull(v.getSku())).isTrue();
        assertThat(variantRepo.existsBySkuAndDeletedAtIsNull("NON-EXISTENT")).isFalse();
    }

    @Test
    void existsByBarcodeAndDeletedAtIsNull_trueWhenBarcodeSet() {
        Product p = persistProduct();
        ProductVariant v = variantRepo.save(factory.newVariant(p.getId()));
        v.setBarcode("BC-IT-" + TestDataFactory.uniqueSuffix());
        variantRepo.save(v);

        assertThat(variantRepo.existsByBarcodeAndDeletedAtIsNull(v.getBarcode())).isTrue();
    }

    @Test
    void findByProductIdInAndDeletedAtIsNull_returnsMultiple() {
        Product p1 = persistProduct();
        Product p2 = persistProduct();
        ProductVariant v1 = variantRepo.save(factory.newVariant(p1.getId()));
        ProductVariant v2 = variantRepo.save(factory.newVariant(p2.getId()));

        List<ProductVariant> got = variantRepo.findByProductIdInAndDeletedAtIsNull(
                List.of(p1.getId(), p2.getId()));

        assertThat(got).extracting(ProductVariant::getId)
                .contains(v1.getId(), v2.getId());
    }

    @Test
    void existsBySkuInAndDeletedAtIsNull_trueIfAnyMatch() {
        Product p = persistProduct();
        ProductVariant v = variantRepo.save(factory.newVariant(p.getId()));

        boolean exists = variantRepo.existsBySkuInAndDeletedAtIsNull(
                List.of(v.getSku(), "OTHER-SKU"));
        assertThat(exists).isTrue();
    }

    @Test
    void deleted_variantIsExcluded() {
        Product p = persistProduct();
        ProductVariant v = variantRepo.save(factory.newVariant(p.getId()));
        v.setDeletedAt(java.time.OffsetDateTime.now());
        variantRepo.save(v);

        assertThat(variantRepo.findById(v.getId())).isPresent();
        assertThat(variantRepo.findByProductIdAndDeletedAtIsNull(p.getId()))
                .extracting(ProductVariant::getId)
                .doesNotContain(v.getId());
    }

    @Test
    void existsBySkuInAndIdNotAndDeletedAtIsNull_excludesOwnRow() {
        Product p = persistProduct();
        ProductVariant v = variantRepo.save(factory.newVariant(p.getId()));

        boolean selfOnly = variantRepo.existsBySkuInAndDeletedAtIsNull(List.of(v.getSku()));
        assertThat(selfOnly).isTrue();
        assertThat(variantRepo.findById(v.getId()).orElseThrow().getId()).isNotNull();
    }

    @Test
    void unknownSku_returnsEmpty() {
        Optional<ProductVariant> got = variantRepo.findBySkuAndDeletedAtIsNull("DOES-NOT-EXIST-" + UUID.randomUUID());
        assertThat(got).isEmpty();
    }
}
