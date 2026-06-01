package fu.osms.catalog.repository;

import fu.osms.catalog.entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {

    List<ProductVariant> findByProductIdAndDeletedAtIsNull(UUID productId);

    List<ProductVariant> findByShopIdAndDeletedAtIsNull(UUID shopId);

    Optional<ProductVariant> findByShopIdAndSkuAndDeletedAtIsNull(UUID shopId, String sku);

    boolean existsByShopIdAndSkuAndDeletedAtIsNull(UUID shopId, String sku);
}
