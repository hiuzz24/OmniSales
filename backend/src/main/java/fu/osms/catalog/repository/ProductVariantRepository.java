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

    @Query("SELECT DISTINCT v FROM ProductVariant v " +
            "WHERE LOWER(v.sku) = :normalizedSku " +
            "AND v.isActive = true " +
            "AND v.deletedAt IS NULL " +
            "AND EXISTS (" +
            "    SELECT 1 FROM ChannelProductVariant cpv " +
            "    WHERE cpv.variant = v " +
            "    AND cpv.channelProduct.channel.deletedAt IS NULL " +
            "    AND cpv.channelProduct.mappingState = 'ACTIVE'" +
            ") " +
            "ORDER BY v.createdAt ASC")
    List<ProductVariant> findActiveMarketplaceMappedByNormalizedSku(@Param("normalizedSku") String normalizedSku);

    @Query("SELECT DISTINCT linkedVariant FROM ChannelProductVariant linked " +
            "JOIN linked.variant linkedVariant " +
            "JOIN linked.channelProduct linkedChannelProduct " +
            "JOIN linkedChannelProduct.channel linkedChannel " +
            "WHERE linkedChannel.deletedAt IS NULL " +
            "AND linkedChannelProduct.mappingState = 'ACTIVE' " +
            "AND linkedVariant.isActive = true " +
            "AND linkedVariant.deletedAt IS NULL " +
            "AND LOWER(COALESCE(linked.externalSku, linkedVariant.sku)) IN (" +
            "    SELECT LOWER(COALESCE(source.externalSku, sourceVariant.sku)) " +
            "    FROM ChannelProductVariant source " +
            "    JOIN source.variant sourceVariant " +
            "    JOIN source.channelProduct sourceChannelProduct " +
            "    JOIN sourceChannelProduct.channel sourceChannel " +
            "    WHERE sourceVariant.id = :variantId " +
            "    AND sourceChannel.deletedAt IS NULL " +
            "    AND sourceChannelProduct.mappingState = 'ACTIVE'" +
            ") " +
            "ORDER BY linkedVariant.createdAt ASC")
    List<ProductVariant> findActiveMarketplaceMappedSharingSkuWithVariantId(@Param("variantId") UUID variantId);
}
