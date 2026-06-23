package fu.osms.channel.repository;

import fu.osms.channel.entity.Channel;
import fu.osms.common.enums.PlatformType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChannelRepository extends JpaRepository<Channel, UUID> {

    List<Channel> findByDeletedAtIsNull();

    List<Channel> findByPlatformAndDeletedAtIsNull(PlatformType platform);

    boolean existsByPlatformAndDisplayName(PlatformType platform, String displayName);

    Optional<Channel> findByPlatformAndDisplayName(PlatformType platform, String displayName);

    @Query(value = "SELECT * FROM channels WHERE platform = 'SHOPIFY' AND metadata->>'shop' = :shop AND deleted_at IS NULL LIMIT 1", nativeQuery = true)
    Optional<Channel> findActiveShopifyByShopDomain(@Param("shop") String shop);
}
