package fu.osms.catalog.repository;

import fu.osms.catalog.entity.Product;
import fu.osms.catalog.enums.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    Page<Product> findByShopIdAndDeletedAtIsNull(UUID shopId, Pageable pageable);

    Page<Product> findByShopIdAndStatusAndDeletedAtIsNull(UUID shopId, ProductStatus status, Pageable pageable);

    Page<Product> findByShopIdAndCategoryIdAndDeletedAtIsNull(UUID shopId, UUID categoryId, Pageable pageable);

    Optional<Product> findByIdAndDeletedAtIsNull(UUID id);

    @Query("SELECT p FROM Product p WHERE p.shop.id = :shopId AND p.deletedAt IS NULL " +
           "AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Product> searchByShopIdAndKeyword(@Param("shopId") UUID shopId,
                                            @Param("keyword") String keyword,
                                            Pageable pageable);
}
