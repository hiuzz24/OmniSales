package fu.osms.audit.repository;

import fu.osms.audit.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findByShopId(UUID shopId, Pageable pageable);

    Page<AuditLog> findByShopIdAndEntityTypeAndEntityId(UUID shopId, String entityType, UUID entityId, Pageable pageable);

    Page<AuditLog> findByShopIdAndActorId(UUID shopId, UUID actorId, Pageable pageable);

    Page<AuditLog> findByShopIdAndPerformedAtBetween(UUID shopId,
                                                       OffsetDateTime from,
                                                       OffsetDateTime to,
                                                       Pageable pageable);
}
