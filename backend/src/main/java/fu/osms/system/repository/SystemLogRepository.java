package fu.osms.system.repository;

import fu.osms.system.entity.SystemLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
public interface SystemLogRepository extends JpaRepository<SystemLog, UUID> {

    Page<SystemLog> findByLevel(String level, Pageable pageable);

    Page<SystemLog> findByComponent(String component, Pageable pageable);

    Page<SystemLog> findByLevelAndLoggedAtBetween(String level,
                                                    OffsetDateTime from,
                                                    OffsetDateTime to,
                                                    Pageable pageable);

    long deleteByLoggedAtBefore(OffsetDateTime loggedAt);

    @Query(value = """
        SELECT 
            s.id as id, 
            s.level as type, 
            s.message as message, 
            'SYSTEM' as user, 
            COALESCE(s.context->>'ip', '127.0.0.1') as ip, 
            s.stack_trace as details, 
            s.logged_at as timestamp 
        FROM system_logs s
        WHERE (:level IS NULL OR :level = 'ALL' OR s.level = :level)
          AND (:keyword IS NULL OR :keyword = '' 
               OR LOWER(s.message) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(s.component) LIKE LOWER(CONCAT('%', :keyword, '%')))
          AND (COALESCE(:from, '-infinity'::timestamptz) <= s.logged_at)
          AND (COALESCE(:to, 'infinity'::timestamptz) >= s.logged_at)
        
        UNION ALL
        
        SELECT 
            a.id as id, 
            CASE WHEN a.action = 'LOGIN' THEN 'LOGIN' ELSE 'INFO' END as type, 
            CASE 
                WHEN a.action = 'LOGIN' THEN CONCAT('Đăng nhập thành công: ', a.actor_email)
                WHEN a.action = 'LOGOUT' THEN CONCAT('Đăng xuất thành công: ', a.actor_email)
                ELSE CONCAT(a.action, ' ', a.entity_type, ' [', COALESCE(a.entity_name, ''), '] bởi ', a.actor_email)
            END as message, 
            a.actor_email as user, 
            COALESCE(a.changes->>'ip', '192.168.1.10') as ip, 
            a.changes::text as details, 
            a.performed_at as timestamp
        FROM audit_logs a
        WHERE (:level IS NULL OR :level = 'ALL' 
               OR (:level = 'LOGIN' AND a.action = 'LOGIN')
               OR (:level = 'INFO' AND a.action <> 'LOGIN'))
          AND (:keyword IS NULL OR :keyword = '' 
               OR LOWER(a.actor_email) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(a.action) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(a.entity_type) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(a.entity_name) LIKE LOWER(CONCAT('%', :keyword, '%')))
          AND (COALESCE(:from, '-infinity'::timestamptz) <= a.performed_at)
          AND (COALESCE(:to, 'infinity'::timestamptz) >= a.performed_at)
          
        ORDER BY timestamp DESC
    """, countQuery = """
        SELECT COUNT(*) FROM (
            SELECT s.id 
            FROM system_logs s
            WHERE (:level IS NULL OR :level = 'ALL' OR s.level = :level)
              AND (:keyword IS NULL OR :keyword = '' 
                   OR LOWER(s.message) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(s.component) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (COALESCE(:from, '-infinity'::timestamptz) <= s.logged_at)
              AND (COALESCE(:to, 'infinity'::timestamptz) >= s.logged_at)
            
            UNION ALL
            
            SELECT a.id
            FROM audit_logs a
            WHERE (:level IS NULL OR :level = 'ALL' 
                   OR (:level = 'LOGIN' AND a.action = 'LOGIN')
                   OR (:level = 'INFO' AND a.action <> 'LOGIN'))
              AND (:keyword IS NULL OR :keyword = '' 
                   OR LOWER(a.actor_email) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(a.action) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(a.entity_type) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(a.entity_name) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (COALESCE(:from, '-infinity'::timestamptz) <= a.performed_at)
              AND (COALESCE(:to, 'infinity'::timestamptz) >= a.performed_at)
        ) AS union_count
    """, nativeQuery = true)
    Page<SystemLogProjection> searchSystemAndAuditLogs(
            @Param("level") String level,
            @Param("keyword") String keyword,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            Pageable pageable);
}
