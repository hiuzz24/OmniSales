package fu.osms.inventory.repository;

import fu.osms.inventory.entity.StocktakeItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StocktakeItemRepository extends JpaRepository<StocktakeItem, UUID> {

    List<StocktakeItem> findBySession_Id(UUID sessionId);

    void deleteBySession_Id(UUID sessionId);

    @Query("SELECT DISTINCT item.variant.id FROM StocktakeItem item " +
            "JOIN ChannelProductVariant cpv ON cpv.variant.id = item.variant.id " +
            "JOIN cpv.channelProduct cp " +
            "JOIN cp.channel ch " +
            "WHERE item.session.status = 'COMPLETED' " +
            "AND item.difference <> 0 " +
            "AND ch.deletedAt IS NULL " +
            "AND cp.mappingState = 'ACTIVE' " +
            "AND (cpv.lastSyncedAt IS NULL OR cpv.lastSyncedAt < COALESCE(item.session.completedAt, item.session.updatedAt))")
    List<UUID> findCompletedVariantIdsPendingMarketplaceSync();

    @Query("SELECT DISTINCT item.variant.id FROM StocktakeItem item " +
            "WHERE item.session.id = :sessionId AND item.session.status = 'COMPLETED' " +
            "AND item.difference <> 0")
    List<UUID> findCompletedVariantIdsBySessionId(@Param("sessionId") UUID sessionId);

    @Query("SELECT COUNT(DISTINCT item.variant.id) FROM StocktakeItem item " +
            "JOIN ChannelProductVariant cpv ON cpv.variant.id = item.variant.id " +
            "JOIN cpv.channelProduct cp " +
            "JOIN cp.channel ch " +
            "WHERE item.session.id = :sessionId " +
            "AND item.session.status = 'COMPLETED' " +
            "AND item.difference <> 0 " +
            "AND ch.deletedAt IS NULL " +
            "AND cp.mappingState = 'ACTIVE' " +
            "AND (cpv.lastSyncedAt IS NULL OR cpv.lastSyncedAt < COALESCE(item.session.completedAt, item.session.updatedAt))")
    long countPendingMarketplaceSyncVariantsBySessionId(@Param("sessionId") UUID sessionId);
}
