package fu.osms.sync.order.pull.repository;

import fu.osms.sync.order.pull.entity.OrderPullJob;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderPullJobRepository extends JpaRepository<OrderPullJob, UUID> {

    boolean existsBySyncLog_Id(UUID syncLogId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select j from OrderPullJob j
            join fetch j.syncLog l
            join fetch l.channel
            where j.id = :id
            """)
    Optional<OrderPullJob> findForUpdateWithContext(@Param("id") UUID id);

    @Query(value = """
            SELECT id
            FROM order_pull_jobs
            WHERE (
                    state = 'PENDING'
                    OR (state = 'PUBLISHED' AND COALESCE(published_at, created_at) <= :publishedCutoff)
                    OR (state = 'PROCESSING' AND COALESCE(last_heartbeat_at, started_at, created_at) <= :processingCutoff)
                  )
            ORDER BY created_at ASC
            FOR UPDATE SKIP LOCKED
            LIMIT :limit
            """, nativeQuery = true)
    List<UUID> claimRecoverableIds(
            @Param("publishedCutoff") OffsetDateTime publishedCutoff,
            @Param("processingCutoff") OffsetDateTime processingCutoff,
            @Param("limit") int limit);
}
