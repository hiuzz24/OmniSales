package fu.osms.channel.repository;

import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelProduct;
import fu.osms.common.enums.SyncStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChannelProductRepository extends JpaRepository<ChannelProduct, UUID> {

    Page<ChannelProduct> findByChannelId(UUID channelId, Pageable pageable);

    List<ChannelProduct> findByChannelId(UUID channelId);

    Optional<ChannelProduct> findByChannelIdAndExternalProductId(UUID channelId, String externalProductId);

    long countByChannelIdAndMappingState(UUID channelId, String mappingState);

    List<ChannelProduct> findByProductIdAndMappingState(UUID productId, String mappingState);

    List<ChannelProduct> findByChannelIdAndSyncStatus(UUID channelId, SyncStatus syncStatus);

    List<ChannelProduct> findByProductIdInAndMappingState(Collection<UUID> productIds, String mappingState);

    List<ChannelProduct> findByProductId(UUID id);

    @Query("SELECT cp FROM ChannelProduct cp " +
            "JOIN FETCH cp.product p " +
            "JOIN FETCH cp.channel ch " +
            "WHERE ch.id = :channelId " +
            "AND ch.deletedAt IS NULL " +
            "AND cp.mappingState = 'ACTIVE' " +
            "AND p.deletedAt IS NULL")
    List<ChannelProduct> findActiveByChannelIdWithProduct(@Param("channelId") UUID channelId);

    @Query("SELECT cp FROM ChannelProduct cp " +
            "JOIN FETCH cp.product p " +
            "JOIN FETCH cp.channel ch " +
            "WHERE ch.id = :channelId " +
            "AND ch.deletedAt IS NULL " +
            "AND cp.mappingState = 'ACTIVE' " +
            "AND p.deletedAt IS NULL " +
            "AND (" +
            "    cp.syncStatus <> :syncedStatus " +
            "    OR cp.updatedAt > :changedSince " +
            "    OR p.updatedAt > :changedSince " +
            "    OR EXISTS (" +
            "        SELECT 1 FROM ProductVariant v " +
            "        WHERE v.product = p " +
            "        AND v.deletedAt IS NULL " +
            "        AND v.updatedAt > :changedSince" +
            "    )" +
            ")")
    List<ChannelProduct> findActiveChangedByChannelIdSince(@Param("channelId") UUID channelId,
                                                           @Param("changedSince") OffsetDateTime changedSince,
                                                           @Param("syncedStatus") SyncStatus syncedStatus);
}
