package fu.osms.channel.repository;

import fu.osms.channel.entity.Channel;
import fu.osms.common.enums.PlatformType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChannelRepository extends JpaRepository<Channel, UUID> {

    List<Channel> findByShopId(UUID shopId);

    List<Channel> findByShopIdAndDeletedAtIsNull(UUID shopId);

    List<Channel> findByShopIdAndPlatform(UUID shopId, PlatformType platform);

    boolean existsByShopIdAndPlatformAndDisplayName(UUID shopId, PlatformType platform, String displayName);
}
