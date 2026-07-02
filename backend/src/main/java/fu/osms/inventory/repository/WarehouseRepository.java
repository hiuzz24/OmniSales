package fu.osms.inventory.repository;

import fu.osms.inventory.entity.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WarehouseRepository extends JpaRepository<Warehouse, UUID> {

    List<Warehouse> findByIsActive(Boolean isActive);

    List<Warehouse> findByDeletedAtIsNull();


    List<Warehouse> findByIsActiveTrueOrderByNameAsc();

    Optional<Warehouse> findFirstByNameAndDeletedAtIsNull(String name);

    @Query("SELECT w FROM Warehouse w WHERE w.id = (SELECT u.warehouse.id FROM User u WHERE u.id = :userId)")
    Warehouse findWarehouseByUserId(@Param("userId") UUID userId);
}
