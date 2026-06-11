package fu.osms.reporting.repository;

import fu.osms.reporting.entity.ReportConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReportConfigRepository extends JpaRepository<ReportConfig, UUID> {

    List<ReportConfig> findByIsActive(Boolean isActive);

    List<ReportConfig> findByReportType(String reportType);
}
