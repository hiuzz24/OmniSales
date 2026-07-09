package fu.osms.system.repository;

import fu.osms.system.entity.ApiEndpointLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiEndpointLimitRepository extends JpaRepository<ApiEndpointLimit, UUID> {
    Optional<ApiEndpointLimit> findByEndpoint(String endpoint);
}
