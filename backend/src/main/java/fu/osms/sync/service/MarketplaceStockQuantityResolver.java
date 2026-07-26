package fu.osms.sync.service;

import fu.osms.channel.entity.ChannelProductVariant;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface MarketplaceStockQuantityResolver {

    Set<UUID> expandVariantIdsBySkuGroup(Collection<UUID> variantIds);

    int maxAvailableQuantityForSkuGroup(ChannelProductVariant mapping);

    Map<UUID, Integer> resolveAvailableByMappingIds(Collection<UUID> mappingIds);
}
