package fu.osms.inventory.repository;

import fu.osms.inventory.entity.StocktakeItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StocktakeItemRepository extends JpaRepository<StocktakeItem, UUID> {

    List<StocktakeItem> findBySession_Id(UUID sessionId);

    void deleteBySession_Id(UUID sessionId);
}
