package fu.osms.catalog.repository;

import fu.osms.catalog.dto.response.CategoryProductCount;
import fu.osms.catalog.entity.Category;
import fu.osms.catalog.entity.Product;
import fu.osms.catalog.enums.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

    Page<Product> findByCategoryIdAndDeletedAtIsNull(UUID categoryId, Pageable pageable);

    boolean existsByCategoryIdAndDeletedAtIsNull(UUID categoryId);

    Optional<Product> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsBySkuAndDeletedAtIsNull(String sku);

    boolean existsByNameAndDeletedAtIsNull(String name);

    boolean existsBySkuInAndDeletedAtIsNull(Collection<String> skus);

    boolean existsBySkuAndIdNotAndDeletedAtIsNull(String sku, UUID id);

    boolean existsByNameAndIdNotAndDeletedAtIsNull(String name, UUID id);

    boolean existsBySkuInAndIdNotAndDeletedAtIsNull(Collection<String> skus, UUID id);

    Optional<Product> findFirstBySkuAndDeletedAtIsNull(String sku);

    Optional<Product> findFirstByNameIgnoreCaseAndDeletedAtIsNullOrderByCreatedAtAsc(String name);

    long count();

    @Query("""
            SELECT
                p.category.id as categoryId,
                COUNT(p.id) as productCount
            FROM Product p
            GROUP BY p.category.id
            """)
    List<CategoryProductCount> countProductsByCategory();
}
