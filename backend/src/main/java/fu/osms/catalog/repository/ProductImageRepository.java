package fu.osms.catalog.repository;

import fu.osms.catalog.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("delete from ProductImage image where image.product.id = :productId and image.variant is null")
    void deleteProductLevelImages(@Param("productId") UUID productId);

    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("delete from ProductImage image where image.variant.id = :variantId")
    void deleteVariantImages(@Param("variantId") UUID variantId);

    boolean existsByProductIdAndIsPrimaryTrue(UUID productId);

    Object findByProductIdOrderBySortOrderAsc(UUID productId);

    Object findByProductIdInOrderBySortOrderAsc(List<Object> objects);
}
