package fu.osms.audit.repository;

import fu.osms.audit.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findByEntityTypeAndEntityId(String entityType, UUID entityId, Pageable pageable);

    Page<AuditLog> findByActorId(UUID actorId, Pageable pageable);

    Page<AuditLog> findByPerformedAtBetween(OffsetDateTime from, OffsetDateTime to, Pageable pageable);

    Page<AuditLog> findByEntityType(String entityType, Pageable pageable);

    Page<AuditLog> findByAction(String action, Pageable pageable);

    Page<AuditLog> findByEntityTypeAndAction(String entityType, String action, Pageable pageable);

    @Query(value = """
        SELECT * FROM audit_logs a
        WHERE a.entity_type = 'ORDER'
          AND (:keyword IS NULL OR :keyword = ''
               OR LOWER(a.entity_name) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(a.actor_email) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(a.entity_id::text) LIKE LOWER(CONCAT('%', :keyword, '%')))
          AND (COALESCE(:from, '-infinity'::timestamptz) <= a.performed_at)
          AND (COALESCE(:to, 'infinity'::timestamptz) >= a.performed_at)
          AND (:action IS NULL OR :action = '' OR a.action = :action)
        ORDER BY a.performed_at DESC NULLS LAST, a.id DESC
    """, countQuery = """
        SELECT COUNT(*) FROM audit_logs a
        WHERE a.entity_type = 'ORDER'
          AND (:keyword IS NULL OR :keyword = ''
               OR LOWER(a.entity_name) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(a.actor_email) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(a.entity_id::text) LIKE LOWER(CONCAT('%', :keyword, '%')))
          AND (COALESCE(:from, '-infinity'::timestamptz) <= a.performed_at)
          AND (COALESCE(:to, 'infinity'::timestamptz) >= a.performed_at)
          AND (:action IS NULL OR :action = '' OR a.action = :action)
    """, nativeQuery = true)
    Page<AuditLog> searchOrderLogs(
            @Param("keyword") String keyword,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            @Param("action") String action,
            Pageable pageable);
}
