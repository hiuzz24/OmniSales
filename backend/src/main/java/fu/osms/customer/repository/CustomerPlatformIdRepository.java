package fu.osms.customer.repository;

import fu.osms.common.enums.PlatformType;
import fu.osms.customer.entity.CustomerPlatformId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerPlatformIdRepository extends JpaRepository<CustomerPlatformId, UUID> {

    Optional<CustomerPlatformId> findByPlatformAndExternalCustomerId(PlatformType platform, String externalCustomerId);
}
