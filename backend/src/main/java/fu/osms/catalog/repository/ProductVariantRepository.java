package fu.osms.catalog.repository;

import fu.osms.catalog.entity.ProductVariant;
import fu.osms.reporting.repository.projection.ProductCatalogProjection;
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

    @Query("""
            SELECT p.id AS productId, v.id AS variantId, v.sku AS sku,
                   p.name AS productName, v.name AS variantName
            FROM ProductVariant v JOIN v.product p
            WHERE v.isActive = true AND v.deletedAt IS NULL
              AND p.deletedAt IS NULL AND p.status = 'ACTIVE'
            ORDER BY p.name, v.sku
            """)
    List<ProductCatalogProjection> findActiveProductReportCatalog();

    List<ProductVariant> findByProductIdAndDeletedAtIsNull(UUID productId);

    List<ProductVariant> findByDeletedAtIsNull();

    Optional<ProductVariant> findBySkuAndDeletedAtIsNull(String sku);

    Optional<ProductVariant> findByProductIdAndSkuAndDeletedAtIsNull(UUID productId, String sku);

    boolean existsBySkuAndDeletedAtIsNull(String sku);

    boolean existsByBarcodeAndDeletedAtIsNull(String barcode);

    List<ProductVariant> findByProductIdInAndDeletedAtIsNull(Collection<UUID> productIds);

    @Query("SELECT v FROM ProductVariant v " +
            "JOIN FETCH v.product p " +
            "WHERE v.deletedAt IS NULL " +
            "AND p.deletedAt IS NULL " +
            "AND NOT EXISTS (" +
            "    SELECT 1 FROM InventoryItem i " +
            "    WHERE i.variant = v" +
            ")")
    List<ProductVariant> findVariantsWithoutInventoryItems();

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

    @Query("SELECT v FROM ProductVariant v JOIN FETCH v.product p " +
            "WHERE v.isActive = true AND v.deletedAt IS NULL " +
            "AND p.deletedAt IS NULL " +
            "AND CAST(p.status AS string) = 'ACTIVE' " +
            "ORDER BY p.name, v.name, v.sku")
    List<ProductVariant> findAllImportableWithProduct();

    @Query("SELECT v FROM ProductVariant v JOIN FETCH v.product p " +
            "WHERE v.id = :id AND v.isActive = true AND v.deletedAt IS NULL " +
            "AND p.deletedAt IS NULL")
    Optional<ProductVariant> findImportableById(@Param("id") UUID id);

}
