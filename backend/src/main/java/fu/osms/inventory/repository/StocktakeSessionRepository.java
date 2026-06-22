package fu.osms.inventory.repository;

import fu.osms.inventory.entity.StocktakeSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface StocktakeSessionRepository extends JpaRepository<StocktakeSession, UUID>, JpaSpecificationExecutor<StocktakeSession> {

    long countByStatus(String status);

    @Query("SELECT COUNT(s) FROM StocktakeSession s WHERE YEAR(s.createdAt) = :year")
    long countByCreatedYear(int year);
}
