package fu.osms.system.repository;

import fu.osms.system.entity.ApiMetricDaily;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiMetricDailyRepository extends JpaRepository<ApiMetricDaily, UUID> {

    Optional<ApiMetricDaily> findByEndpointAndMethodAndRecordedDateAndRecordedHour(
            String endpoint, String method, LocalDate recordedDate, Integer recordedHour);

    List<ApiMetricDaily> findByRecordedDate(LocalDate date);

    @Query("SELECT m FROM ApiMetricDaily m WHERE m.recordedDate >= :startDate ORDER BY m.recordedDate ASC, m.recordedHour ASC")
    List<ApiMetricDaily> findMetricsFromDate(@Param("startDate") LocalDate startDate);

    @Query("SELECT m.endpoint as endpoint, m.method as method, " +
           "SUM(m.requestCount) as totalRequests, " +
           "SUM(m.successCount) as totalSuccess, " +
           "SUM(m.failCount) as totalFail, " +
           "AVG(m.avgLatencyMs) as averageLatency " +
           "FROM ApiMetricDaily m " +
           "WHERE m.recordedDate = :date " +
           "GROUP BY m.endpoint, m.method")
    List<Object[]> getDailySummaryGroupedByEndpoint(@Param("date") LocalDate date);
}
