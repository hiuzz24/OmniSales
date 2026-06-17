package fu.osms.catalog.repository;

import fu.osms.catalog.entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {

    List<ProductVariant> findByProductIdAndDeletedAtIsNull(UUID productId);

    List<ProductVariant> findByProductIdInAndDeletedAtIsNull(Collection<UUID> productIds);

    List<ProductVariant> findByDeletedAtIsNull();

    Optional<ProductVariant> findBySkuAndDeletedAtIsNull(String sku);

    boolean existsBySkuAndDeletedAtIsNull(String sku);

    boolean existsByBarcodeAndDeletedAtIsNull(String barcode);

    boolean existsBySkuInAndDeletedAtIsNull(Collection<String> skus);

    boolean existsByBarcodeInAndDeletedAtIsNull(Collection<String> barcodes);

    boolean existsBySkuInAndProductIdNotAndDeletedAtIsNull(Collection<String> skus, UUID productId);

    boolean existsByBarcodeInAndProductIdNotAndDeletedAtIsNull(Collection<String> barcodes, UUID productId);
}
