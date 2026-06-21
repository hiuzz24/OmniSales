package fu.osms.auth.repository;

import fu.osms.auth.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByName(String name);

    boolean existsByName(String name);


    @Query("""
            SELECT r
            FROM Role r
            JOIN UserRole ur ON ur.role.id = r.id
            WHERE ur.user.id = :userId
            """)
    Optional<Role> findRoleByUserId(@Param("userId") UUID userId);
}
