package fu.osms.channel.repository;

import fu.osms.channel.entity.ChannelCredential;
import fu.osms.common.enums.PlatformType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChannelCredentialRepository extends JpaRepository<ChannelCredential, UUID> {

    @EntityGraph(attributePaths = "channel")
    Optional<ChannelCredential> findByChannelId(UUID channelId);

    @EntityGraph(attributePaths = "channel")
    Optional<ChannelCredential> findByChannelIdAndConnectionState(UUID channelId, String connectionState);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "channel")
    @Query("select c from ChannelCredential c where c.channel.id = :channelId")
    Optional<ChannelCredential> findByChannelIdForUpdate(@Param("channelId") UUID channelId);

    @EntityGraph(attributePaths = "channel")
    @Query("""
            select c from ChannelCredential c
            where c.connectionState = 'CONNECTED'
              and c.channel.deletedAt is null
              and c.channel.platform in :platforms
              and c.tokenExpiresAt is not null
              and c.tokenExpiresAt <= :cutoff
            order by c.tokenExpiresAt asc
            """)
    List<ChannelCredential> findExpiringCredentials(@Param("platforms") Collection<PlatformType> platforms,
                                                    @Param("cutoff") OffsetDateTime cutoff);
}
