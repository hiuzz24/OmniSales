package fu.osms.sync.tiktok;

import java.util.Collection;
import java.util.UUID;

public interface TikTokProductDetailEnrichmentService {

    void enrichChannelProducts(UUID channelId, Collection<UUID> channelProductIds);
}
