package fu.osms.auth.repository;

import fu.osms.auth.entity.User;
import fu.osms.auth.enums.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    User findUserById(UUID id);
    java.util.List<User> findByStatus(UserStatus status);
    java.util.List<User> findByWarehouseId(UUID warehouseId);

    @Query("SELECT COUNT(u) FROM User u WHERE u.warehouse.id = :warehouseId AND u.deletedAt IS NULL")
    Integer countByWarehouseId(@Param("warehouseId") UUID warehouseId);

    @Query("SELECT u FROM User u WHERE u.id = :id AND u.deletedAt IS NULL")
    Optional<User> findActiveById(@Param("id") UUID id);
}
