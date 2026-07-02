package fu.osms.sync.repository;

import fu.osms.sync.entity.WebhookEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import fu.osms.common.enums.PlatformType;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID>, JpaSpecificationExecutor<WebhookEvent> {

    Page<WebhookEvent> findByChannel_Id(UUID channelId, Pageable pageable);

    Page<WebhookEvent> findByChannel_IdAndStatus(UUID channelId, String status, Pageable pageable);

    Optional<WebhookEvent> findByPlatformAndExternalEventId(PlatformType platform, String externalEventId);
}
