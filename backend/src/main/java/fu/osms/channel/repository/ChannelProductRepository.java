package fu.osms.channel.repository;

import fu.osms.channel.entity.ChannelProduct;
import fu.osms.common.enums.SyncStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChannelProductRepository extends JpaRepository<ChannelProduct, UUID> {

    Page<ChannelProduct> findByChannelId(UUID channelId, Pageable pageable);

    Optional<ChannelProduct> findByChannelIdAndExternalProductId(UUID channelId, String externalProductId);

    List<ChannelProduct> findByProductIdAndMappingState(UUID productId, String mappingState);

    List<ChannelProduct> findByChannelIdAndSyncStatus(UUID channelId, SyncStatus syncStatus);

    List<ChannelProduct> findByProductIdInAndMappingState(Collection<UUID> productIds, String mappingState);

    List<ChannelProduct> findByProductId(UUID id);
}
