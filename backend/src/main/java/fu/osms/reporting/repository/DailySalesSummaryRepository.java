package fu.osms.reporting.repository;

import fu.osms.reporting.entity.DailySalesSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DailySalesSummaryRepository extends JpaRepository<DailySalesSummary, UUID> {

    Optional<DailySalesSummary> findByChannelIdAndDate(UUID channelId, LocalDate date);

    List<DailySalesSummary> findByDateBetweenOrderByDateAsc(LocalDate from, LocalDate to);

    @Query("SELECT d FROM DailySalesSummary d WHERE d.date BETWEEN :from AND :to AND d.channel.id = :channelId")
    List<DailySalesSummary> findByChannelAndDateRange(@Param("channelId") UUID channelId,
                                                       @Param("from") LocalDate from,
                                                       @Param("to") LocalDate to);
}
