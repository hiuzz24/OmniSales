package fu.osms.channel.repository;

import fu.osms.channel.entity.Channel;
import fu.osms.common.enums.PlatformType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChannelRepository extends JpaRepository<Channel, UUID> {

    List<Channel> findByDeletedAtIsNull();

    List<Channel> findByPlatform(PlatformType platform);

    boolean existsByPlatformAndDisplayName(PlatformType platform, String displayName);
}
