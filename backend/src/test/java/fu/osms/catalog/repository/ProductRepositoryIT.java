package fu.osms.catalog.repository;

import fu.osms.catalog.dto.response.CategoryProductCount;
import fu.osms.catalog.entity.Category;
import fu.osms.catalog.entity.Product;
import fu.osms.it.IntegrationTestCleanupHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ProductRepository}.
 *
 * <p>Real PostgreSQL ({@code osms_it}). Uses {@link JdbcTemplate} to
 * insert test categories then exercises the repository's custom JPQL
 * queries — in particular the {@code countProductsByCategory}
 * projection.</p>
 */
@SpringBootTest
@ActiveProfiles("it")
class ProductRepositoryIT {

    @Autowired ProductRepository repository;
    @Autowired JdbcTemplate jdbc;
    @Autowired IntegrationTestCleanupHelper cleanup;

    @BeforeEach
    void setUp() {
        cleanup.truncate();
    }

    @Test
    void saveAndFindById_roundtrip() {
        UUID catId = insertCategory("Test Cat");
        Product p = Product.builder()
                .category(categoryRef(catId))
                .sku("SKU-IT-1")
                .name("Product A")
                .status(fu.osms.catalog.enums.ProductStatus.ACTIVE)
                .attributes(new java.util.HashMap<>())
                .build();

        Product saved = repository.save(p);
        assertThat(saved.getId()).isNotNull();

        var found = repository.findByIdAndDeletedAtIsNull(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getSku()).isEqualTo("SKU-IT-1");
    }

    @Test
    void existsBySku_findsExistingProduct() {
        UUID catId = insertCategory("Cat 2");
        repository.save(Product.builder()
                .category(categoryRef(catId))
                .sku("UNIQUE-SKU")
                .name("Unique")
                .status(fu.osms.catalog.enums.ProductStatus.ACTIVE)
                .attributes(new java.util.HashMap<>())
                .build());

        assertThat(repository.existsBySkuAndDeletedAtIsNull("UNIQUE-SKU")).isTrue();
        assertThat(repository.existsBySkuAndDeletedAtIsNull("MISSING-SKU")).isFalse();
    }

    @Test
    void countProductsByCategory_groupsByCategory() {
        UUID catA = insertCategory("Cat A");
        UUID catB = insertCategory("Cat B");

        repository.save(Product.builder()
                .category(categoryRef(catA))
                .sku("A-1").name("A1")
                .status(fu.osms.catalog.enums.ProductStatus.ACTIVE)
                .attributes(new java.util.HashMap<>()).build());
        repository.save(Product.builder()
                .category(categoryRef(catA))
                .sku("A-2").name("A2")
                .status(fu.osms.catalog.enums.ProductStatus.ACTIVE)
                .attributes(new java.util.HashMap<>()).build());
        repository.save(Product.builder()
                .category(categoryRef(catB))
                .sku("B-1").name("B1")
                .status(fu.osms.catalog.enums.ProductStatus.ACTIVE)
                .attributes(new java.util.HashMap<>()).build());

        List<CategoryProductCount> counts = repository.countProductsByCategory();

        Map<UUID, Long> byCat = counts.stream()
                .collect(java.util.stream.Collectors.toMap(
                        CategoryProductCount::getCategoryId,
                        CategoryProductCount::getProductCount));
        assertThat(byCat).containsEntry(catA, 2L).containsEntry(catB, 1L);
    }

    @Test
    void count_returnsTotalProducts() {
        UUID catId = insertCategory("Cat X");
        repository.save(Product.builder().category(categoryRef(catId))
                .sku("X-1").name("X1").status(fu.osms.catalog.enums.ProductStatus.ACTIVE)
                .attributes(new java.util.HashMap<>()).build());
        repository.save(Product.builder().category(categoryRef(catId))
                .sku("X-2").name("X2").status(fu.osms.catalog.enums.ProductStatus.ACTIVE)
                .attributes(new java.util.HashMap<>()).build());

        assertThat(repository.count()).isEqualTo(2L);
    }

    private UUID insertCategory(String name) {
        String slug = "slug-" + System.nanoTime();
        return jdbc.queryForObject(
                "INSERT INTO categories (id, name, slug, sort_order, status) " +
                "VALUES (gen_random_uuid(), ?, ?, 1, 'ACTIVE') RETURNING id",
                UUID.class, name, slug);
    }

    private Category categoryRef(UUID id) {
        return Category.builder().id(id).build();
    }
}