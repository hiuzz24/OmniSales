package fu.osms.system.repository;

import fu.osms.system.entity.BackupFile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface BackupFileRepository extends JpaRepository<BackupFile, UUID> {
    Page<BackupFile> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
