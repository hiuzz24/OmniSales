package fu.osms.sync.repository;

import fu.osms.sync.entity.WebhookEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {

    Page<WebhookEvent> findByChannelId(UUID channelId, Pageable pageable);

    Page<WebhookEvent> findByChannelIdAndStatus(UUID channelId, String status, Pageable pageable);
}
