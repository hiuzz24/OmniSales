package fu.osms.catalog.repository;

import fu.osms.catalog.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findByShopIdOrderBySortOrderAsc(UUID shopId);

    List<Category> findByShopIdAndParentIsNull(UUID shopId);

    List<Category> findByParentId(UUID parentId);

    Optional<Category> findByShopIdAndSlug(UUID shopId, String slug);

    boolean existsByShopIdAndSlug(UUID shopId, String slug);
}
