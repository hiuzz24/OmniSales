package fu.osms.channel.repository;

import fu.osms.channel.entity.ChannelCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChannelCredentialRepository extends JpaRepository<ChannelCredential, UUID> {

    Optional<ChannelCredential> findByChannelId(UUID channelId);

    Optional<ChannelCredential> findByChannelIdAndConnectionState(UUID channelId, String connectionState);
}
