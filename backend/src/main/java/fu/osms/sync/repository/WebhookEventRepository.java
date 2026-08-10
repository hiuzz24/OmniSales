package fu.osms.sync.repository;

import fu.osms.sync.entity.WebhookEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import fu.osms.common.enums.PlatformType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID>, JpaSpecificationExecutor<WebhookEvent> {

    Page<WebhookEvent> findByChannel_Id(UUID channelId, Pageable pageable);

    Page<WebhookEvent> findByChannel_IdAndStatus(UUID channelId, String status, Pageable pageable);

    Optional<WebhookEvent> findByPlatformAndExternalEventId(PlatformType platform, String externalEventId);

    @EntityGraph(attributePaths = "channel")
    Optional<WebhookEvent> findWithChannelById(UUID id);

    @Query(value = """
            SELECT id
            FROM webhook_events
            WHERE (
                    (
                      status IN ('RECEIVED', 'PROCESSING')
                      AND received_at <= :stuckCutoff
                    )
                    OR
                    (
                      status = 'FAILED'
                      AND retry_count < :maxRetries
                      AND channel_id IS NOT NULL
                    )
                  )
            ORDER BY received_at ASC
            FOR UPDATE SKIP LOCKED
            LIMIT :limit
            """, nativeQuery = true)
    List<UUID> claimRetryableWebhookEventIds(
            @Param("stuckCutoff") java.time.OffsetDateTime stuckCutoff,
            @Param("maxRetries") int maxRetries,
            @Param("limit") int limit);
}
