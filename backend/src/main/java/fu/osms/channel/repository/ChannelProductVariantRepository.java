package fu.osms.channel.repository;

import fu.osms.channel.entity.ChannelProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChannelProductVariantRepository extends JpaRepository<ChannelProductVariant, UUID> {

    List<ChannelProductVariant> findByChannelProductId(UUID channelProductId);

    Optional<ChannelProductVariant> findByChannelProductIdAndVariantId(UUID channelProductId, UUID variantId);

    Optional<ChannelProductVariant> findByChannelProductIdAndExternalVariantId(UUID channelProductId,
                                                                                 String externalVariantId);

    @Query("SELECT COUNT(cpv) FROM ChannelProductVariant cpv " +
            "WHERE cpv.channelProduct.channel.id = :channelId " +
            "AND cpv.channelProduct.mappingState = 'ACTIVE'")
    long countActiveByChannelId(@Param("channelId") UUID channelId);

    @Query("SELECT cpv FROM ChannelProductVariant cpv " +
            "JOIN FETCH cpv.channelProduct cp " +
            "JOIN FETCH cp.channel ch " +
            "JOIN FETCH cpv.variant v " +
            "LEFT JOIN FETCH v.product " +
            "WHERE ch.id = :channelId " +
            "AND ch.deletedAt IS NULL " +
            "AND cp.mappingState = 'ACTIVE'")
    List<ChannelProductVariant> findActiveByChannelIdWithVariant(@Param("channelId") UUID channelId);

    @Query("SELECT cpv FROM ChannelProductVariant cpv " +
            "JOIN FETCH cpv.channelProduct cp " +
            "JOIN FETCH cp.channel ch " +
            "JOIN FETCH cpv.variant v " +
            "LEFT JOIN FETCH v.product " +
            "WHERE ch.id = :channelId " +
            "AND v.id IN :variantIds " +
            "AND ch.deletedAt IS NULL " +
            "AND cp.mappingState = 'ACTIVE'")
    List<ChannelProductVariant> findActiveByChannelIdAndVariantIdInWithVariant(
            @Param("channelId") UUID channelId,
            @Param("variantIds") List<UUID> variantIds);

    @Query(value = "SELECT cpv.* FROM channel_product_variants cpv " +
            "JOIN channel_products cp ON cp.id = cpv.channel_product_id " +
            "JOIN channels ch ON ch.id = cp.channel_id " +
            "WHERE cpv.variant_id = :variantId " +
            "AND CAST(ch.platform AS text) = 'LAZADA' " +
            "AND ch.deleted_at IS NULL " +
            "AND cp.mapping_state = 'ACTIVE'",
            nativeQuery = true)
    List<ChannelProductVariant> findActiveLazadaByVariantId(@Param("variantId") UUID variantId);
}
