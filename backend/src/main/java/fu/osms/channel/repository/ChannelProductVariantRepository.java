package fu.osms.channel.repository;

import fu.osms.channel.entity.ChannelProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChannelProductVariantRepository extends JpaRepository<ChannelProductVariant, UUID> {

    List<ChannelProductVariant> findByChannelProductId(UUID channelProductId);

    Optional<ChannelProductVariant> findByChannelProductIdAndVariantId(UUID channelProductId, UUID variantId);

    Optional<ChannelProductVariant> findByChannelProductIdAndExternalVariantId(UUID channelProductId,
                                                                                 String externalVariantId);
}
