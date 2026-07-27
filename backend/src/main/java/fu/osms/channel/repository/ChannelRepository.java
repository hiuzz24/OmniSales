package fu.osms.channel.repository;

import fu.osms.channel.entity.Channel;
import fu.osms.common.enums.PlatformType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChannelRepository extends JpaRepository<Channel, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Channel c where c.id = :id")
    Optional<Channel> findForUpdateById(@Param("id") UUID id);

    List<Channel> findByDeletedAtIsNull();

    List<Channel> findByPlatformAndDeletedAtIsNull(PlatformType platform);

    boolean existsByPlatformAndDisplayName(PlatformType platform, String displayName);

    Optional<Channel> findByPlatformAndDisplayName(PlatformType platform, String displayName);

    @Query(value = """
            SELECT * FROM channels
            WHERE platform = 'SHOPIFY'
              AND regexp_replace(
                    regexp_replace(
                      regexp_replace(lower(trim(COALESCE(metadata->>'shopDomain', metadata->>'shop', display_name))),
                                     '^https?://', '', 'i'),
                      '[/\\?#].*$', ''),
                    '\\.myshopify\\.com$', '', 'i') = :handle
            ORDER BY CASE WHEN deleted_at IS NULL THEN 0 ELSE 1 END, created_at
            """, nativeQuery = true)
    List<Channel> findShopifyCandidatesByHandle(@Param("handle") String handle);

    @Query(value = """
            SELECT * FROM channels
            WHERE platform = 'LAZADA'
              AND metadata->>'accountId' = :accountId
            ORDER BY CASE WHEN deleted_at IS NULL THEN 0 ELSE 1 END, created_at
            """, nativeQuery = true)
    List<Channel> findLazadaCandidatesByAccountId(@Param("accountId") String accountId);

    @Query(value = """
            SELECT * FROM channels
            WHERE platform = 'TIKTOK'
              AND ((:shopId <> '' AND metadata->>'shopId' = :shopId)
                OR (:openId <> '' AND metadata->>'openId' = :openId)
                OR (:accountId <> '' AND metadata->>'accountId' = :accountId))
            ORDER BY CASE WHEN deleted_at IS NULL THEN 0 ELSE 1 END, created_at
            """, nativeQuery = true)
    List<Channel> findTikTokCandidatesByIdentity(@Param("shopId") String shopId,
                                                  @Param("openId") String openId,
                                                  @Param("accountId") String accountId);


    @Query(value = "SELECT * FROM channels " +
            "WHERE platform = 'TIKTOK' " +
            "AND deleted_at IS NULL " +
            "AND metadata->>'shopId' = :shopId " +
            "LIMIT 1", nativeQuery = true)
    Optional<Channel> findActiveTikTokByShopId(@Param("shopId") String shopId);

    @Query(value = """
            SELECT * FROM channels
            WHERE platform = 'TIKTOK'
              AND deleted_at IS NULL
              AND (
                    (:openId <> '' AND (
                        metadata->>'openId' = :openId
                        OR metadata->>'accountId' = :openId
                    ))
                    OR (:sellerId <> '' AND metadata->>'shopId' = :sellerId)
                  )
            ORDER BY created_at
            """, nativeQuery = true)
    List<Channel> findActiveTikTokByWebhookIdentity(
            @Param("openId") String openId,
            @Param("sellerId") String sellerId);

}
