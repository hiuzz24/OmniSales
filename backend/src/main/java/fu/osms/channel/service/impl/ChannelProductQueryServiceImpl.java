package fu.osms.channel.service.impl;

import fu.osms.catalog.service.ProductChannelConfigService;
import fu.osms.channel.dto.response.ChannelProductResponse;
import fu.osms.channel.dto.response.ChannelSyncResponse;
import fu.osms.channel.entity.ChannelProduct;
import fu.osms.channel.repository.ChannelProductRepository;
import fu.osms.channel.service.ChannelProductQueryService;
import fu.osms.common.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChannelProductQueryServiceImpl implements ChannelProductQueryService {

    private final ChannelProductRepository channelProductRepository;
    private final ProductChannelConfigService productChannelConfigService;

    @Override
    public PageResponse<ChannelProductResponse> getChannelProducts(UUID channelId, int page, int size) {
        throw new UnsupportedOperationException("Chưa code");
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, List<String>> getProductChannels(Collection<UUID> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return activeMappings(productIds).stream()
                .filter(cp -> cp.getProduct() != null && cp.getChannel() != null)
                .filter(cp -> cp.getExternalProductId() != null && !cp.getExternalProductId().isBlank())
                .collect(Collectors.groupingBy(
                        cp -> cp.getProduct().getId(),
                        Collectors.mapping(
                                cp -> cp.getChannel().getPlatform().name(),
                                Collectors.collectingAndThen(Collectors.toList(), this::distinct)
                        )
                ));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, List<UUID>> getProductChannelIds(Collection<UUID> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return activeMappings(productIds).stream()
                .filter(cp -> cp.getProduct() != null && cp.getChannel() != null)
                .collect(Collectors.groupingBy(
                        cp -> cp.getProduct().getId(),
                        Collectors.mapping(
                                cp -> cp.getChannel().getId(),
                                Collectors.collectingAndThen(Collectors.toList(), this::distinct)
                        )
                ));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, List<ChannelSyncResponse>> getProductChannelSyncs(Collection<UUID> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return activeMappings(productIds).stream()
                .filter(cp -> cp.getProduct() != null && cp.getChannel() != null)
                .collect(Collectors.groupingBy(
                        cp -> cp.getProduct().getId(),
                        Collectors.mapping(this::toSyncResponse, Collectors.toList())
                ));
    }

    private List<ChannelProduct> activeMappings(Collection<UUID> productIds) {
        return channelProductRepository.findByProductIdInAndMappingState(productIds, "ACTIVE");
    }

    private ChannelSyncResponse toSyncResponse(ChannelProduct mapping) {
        return ChannelSyncResponse.builder()
                .channelId(mapping.getChannel().getId())
                .channelName(mapping.getChannel().getDisplayName())
                .platform(mapping.getChannel().getPlatform().name())
                .syncStatus(mapping.getSyncStatus())
                .lastSyncedAt(mapping.getLastSyncedAt())
                .lastSyncError(mapping.getLastSyncError())
                .readyToSync(productChannelConfigService.isReady(mapping))
                .configurationError(productChannelConfigService.configurationError(mapping))
                .platformConfig(platformConfig(mapping))
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> platformConfig(ChannelProduct mapping) {
        if (mapping.getMetadata() == null) {
            return Collections.emptyMap();
        }
        Object value = mapping.getMetadata().get("platformConfig");
        return value instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Collections.emptyMap();
    }

    private <T> List<T> distinct(List<T> values) {
        return values.stream().distinct().toList();
    }
}
