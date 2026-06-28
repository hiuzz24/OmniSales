package fu.osms.catalog.repository;

import fu.osms.catalog.entity.Category;
import fu.osms.catalog.enums.CategoryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findAllByOrderBySortOrderAsc();

    List<Category> findByParentIsNull();

    List<Category> findByParentId(UUID parentId);

    Optional<Category> findBySlug(String slug);

    boolean existsBySlug(String slug);

    Optional<Category> findFirstByNameIgnoreCase(String name);

    long countByStatus(CategoryStatus status);

    long countByParentIsNull();

    Page<Category> findAll(Pageable pageable);
}
