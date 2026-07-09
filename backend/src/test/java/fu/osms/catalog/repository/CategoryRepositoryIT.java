package fu.osms.catalog.repository;

import fu.osms.catalog.entity.Category;
import fu.osms.catalog.enums.CategoryStatus;
import fu.osms.it.IntegrationTestBase;
import fu.osms.it.IntegrationTestCleanupHelper;
import fu.osms.it.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryRepositoryIT extends IntegrationTestBase {

    @Autowired CategoryRepository categoryRepo;
    @Autowired TestDataFactory factory;
    @Autowired IntegrationTestCleanupHelper cleanup;

    @BeforeEach
    void setUp() {
        cleanup.truncate();
    }

    @Test
    void findBySlug_returnsExisting() {
        Category cat = categoryRepo.save(factory.newCategory());

        Optional<Category> got = categoryRepo.findBySlug(cat.getSlug());
        assertThat(got).isPresent();
        assertThat(got.get().getId()).isEqualTo(cat.getId());
    }

    @Test
    void existsBySlug_trueForExisting() {
        Category cat = categoryRepo.save(factory.newCategory());

        assertThat(categoryRepo.existsBySlug(cat.getSlug())).isTrue();
        assertThat(categoryRepo.existsBySlug("nonexistent-slug")).isFalse();
    }

    @Test
    void findRoots_returnsCategoriesWithoutParent() {
        Category root = categoryRepo.save(factory.newCategory());

        List<Category> roots = categoryRepo.findByParentIsNull();
        assertThat(roots).extracting(Category::getId).contains(root.getId());
    }

    @Test
    void findByParentId_returnsChildren() {
        Category parent = categoryRepo.save(factory.newCategory());
        Category child = factory.newCategory();
        child.setParent(parent);
        categoryRepo.save(child);

        List<Category> children = categoryRepo.findByParentId(parent.getId());
        assertThat(children).extracting(Category::getId).contains(child.getId());
    }

    @Test
    void countByStatus_returnsMatchingCount() {
        Category active = categoryRepo.save(factory.newCategory());
        active.setStatus(CategoryStatus.ACTIVE);
        categoryRepo.save(active);

        Category inactive = factory.newCategory();
        inactive.setStatus(CategoryStatus.INACTIVE);
        categoryRepo.save(inactive);

        long count = categoryRepo.countByStatus(CategoryStatus.ACTIVE);
        assertThat(count).isGreaterThanOrEqualTo(1L);
    }

    @Test
    void findFirstByNameIgnoreCase_isCaseInsensitive() {
        Category cat = categoryRepo.save(factory.newCategory());

        Optional<Category> got = categoryRepo.findFirstByNameIgnoreCase(cat.getName().toUpperCase());
        assertThat(got).isPresent();
    }

    @Test
    void findAllByOrderBySortOrderAsc_returnsOrdered() {
        Category c1 = factory.newCategory();
        c1.setSortOrder(2);
        Category c2 = factory.newCategory();
        c2.setSortOrder(1);
        categoryRepo.save(c1);
        categoryRepo.save(c2);

        List<Category> all = categoryRepo.findAllByOrderBySortOrderAsc();
        // First should have lower sort order (1) → c2
        assertThat(all.get(0).getSortOrder()).isLessThanOrEqualTo(all.get(1).getSortOrder());
    }
}
