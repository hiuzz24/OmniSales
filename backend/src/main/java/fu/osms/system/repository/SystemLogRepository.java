package fu.osms.system.repository;

import fu.osms.system.entity.SystemLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
