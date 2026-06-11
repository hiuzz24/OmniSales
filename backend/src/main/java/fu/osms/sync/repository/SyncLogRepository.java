package fu.osms.sync.repository;

import fu.osms.common.enums.SyncStatus;
import fu.osms.sync.entity.SyncLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SyncLogRepository extends JpaRepository<SyncLog, UUID> {

    Page<SyncLog> findByStatus(SyncStatus status, Pageable pageable);

    Optional<SyncLog> findByIdempotencyKey(String idempotencyKey);
}
