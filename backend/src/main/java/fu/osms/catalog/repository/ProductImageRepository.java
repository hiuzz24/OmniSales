package fu.osms.catalog.repository;

import fu.osms.catalog.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, UUID> {

    List<ProductImage> findByProductIdOrderByIsPrimaryDescSortOrderAsc(UUID productId);

    List<ProductImage> findByProductIdInOrderByIsPrimaryDescSortOrderAsc(List<UUID> productIds);

    List<ProductImage> findByVariantIdOrderBySortOrderAsc(UUID variantId);

    void deleteByProductId(UUID productId);

    Object findByProductIdOrderBySortOrderAsc(UUID productId);

    Object findByProductIdInOrderBySortOrderAsc(List<Object> objects);
}
