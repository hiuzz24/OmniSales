package fu.osms.catalog.repository;

import fu.osms.catalog.entity.ProductVariant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {

    List<ProductVariant> findByProductIdAndDeletedAtIsNull(UUID productId);

    List<ProductVariant> findByDeletedAtIsNull();

    Optional<ProductVariant> findBySkuAndDeletedAtIsNull(String sku);

    Optional<ProductVariant> findByProductIdAndSkuAndDeletedAtIsNull(UUID productId, String sku);

    boolean existsBySkuAndDeletedAtIsNull(String sku);

    boolean existsByBarcodeAndDeletedAtIsNull(String barcode);

    List<ProductVariant> findByProductIdInAndDeletedAtIsNull(Collection<UUID> productIds);

    boolean existsBySkuInAndDeletedAtIsNull(Collection<String> skus);

    boolean existsByBarcodeInAndDeletedAtIsNull(Collection<String> barcodes);

    boolean existsBySkuInAndProductIdNotAndDeletedAtIsNull(Collection<String> skus, UUID productId);

    boolean existsByBarcodeInAndProductIdNotAndDeletedAtIsNull(Collection<String> barcodes, UUID productId);

    @Query("SELECT v FROM ProductVariant v JOIN v.product p " +
            "WHERE v.isActive = true AND v.deletedAt IS NULL " +
            "AND (:keyword IS NULL " +
            "     OR LOWER(v.sku) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')) " +
            "     OR LOWER(p.name) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')))")
    Page<ProductVariant> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT v FROM ProductVariant v " +
            "WHERE v.isActive = true AND v.deletedAt IS NULL " +
            "ORDER BY v.createdAt DESC")
    Page<ProductVariant> findAllActive(Pageable pageable);
}
