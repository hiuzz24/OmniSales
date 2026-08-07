package fu.osms.sync.repository;

import fu.osms.common.enums.SyncStatus;
import fu.osms.sync.entity.SyncLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;
import java.time.OffsetDateTime;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

@Repository
public interface SyncLogRepository extends JpaRepository<SyncLog, UUID>, JpaSpecificationExecutor<SyncLog> {

    Page<SyncLog> findByStatus(SyncStatus status, Pageable pageable);

    Optional<SyncLog> findByIdempotencyKey(String idempotencyKey);

    Page<SyncLog> findAllByOrderByStartedAtDesc(Pageable pageable);

    List<SyncLog> findByJobTypeAndChannelIdAndStatus(String jobType, UUID channelId, SyncStatus status);

    @Query("select s from SyncLog s join fetch s.channel where s.jobType = :jobType and s.status = :status order by s.startedAt desc")
    List<SyncLog> findActiveJobs(@Param("jobType") String jobType, @Param("status") SyncStatus status);

    @Query("select s from SyncLog s where s.jobType = :jobType and s.channel.id = :channelId " +
            "and s.status = 'PENDING' and s.startedAt < :cutoff")
    List<SyncLog> findStalePending(@Param("jobType") String jobType, @Param("channelId") UUID channelId,
                                   @Param("cutoff") OffsetDateTime cutoff);

    @Query("select s from SyncLog s join fetch s.channel where s.id = :id")
    Optional<SyncLog> findWithChannelById(@Param("id") UUID id);

    @EntityGraph(attributePaths = "channel")
    Optional<SyncLog> findFirstByChannelIdAndStatusAndCompletedAtIsNullOrderByStartedAtDesc(
            UUID channelId,
            SyncStatus status
    );

    @EntityGraph(attributePaths = "channel")
    Optional<SyncLog> findFirstByChannelIdAndStatusAndCompletedAtIsNullAndJobTypeEndingWithOrderByStartedAtDesc(
            UUID channelId,
            SyncStatus status,
            String jobTypeSuffix
    );

}
