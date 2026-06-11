package fu.osms.inventory.repository;

import fu.osms.inventory.entity.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WarehouseRepository extends JpaRepository<Warehouse, UUID> {

    List<Warehouse> findByIsActive(Boolean isActive);

    List<Warehouse> findByDeletedAtIsNull();
}
