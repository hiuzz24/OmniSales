package fu.osms.channel.service.impl;

import fu.osms.channel.dto.request.ChannelRequest;
import fu.osms.channel.dto.response.ChannelCredentialResponse;
import fu.osms.channel.dto.response.ChannelProductResponse;
import fu.osms.channel.dto.response.ChannelResponse;
import fu.osms.channel.dto.response.ChannelSyncResponse;
import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelProduct;
import fu.osms.channel.mapper.ChannelCredentialMapper;
import fu.osms.channel.mapper.ChannelMapper;
import fu.osms.channel.mapper.ChannelProductMapper;
import fu.osms.channel.repository.ChannelCredentialRepository;
import fu.osms.channel.repository.ChannelProductRepository;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.channel.service.ChannelService;
import fu.osms.common.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChannelServiceImpl implements ChannelService {

    private final ChannelRepository channelRepository;
    private final ChannelCredentialRepository credentialRepository;
    private final ChannelProductRepository channelProductRepository;
    private final ChannelMapper channelMapper;
    private final ChannelCredentialMapper credentialMapper;
    private final ChannelProductMapper channelProductMapper;

    @Override
    @Transactional
    public ChannelResponse create(ChannelRequest request) {
        throw new UnsupportedOperationException("Chưa code");
    }

    @Override
    @Transactional(readOnly = true)
    public ChannelResponse getById(UUID id) {
        throw new UnsupportedOperationException("Chưa code");
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChannelResponse> getAll() {
        return channelRepository.findByDeletedAtIsNull()
                .stream()
                .map(channelMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public ChannelResponse update(UUID id, ChannelRequest request) {
        throw new UnsupportedOperationException("Chưa code");
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        throw new UnsupportedOperationException("Chưa code");
    }

    @Override
    @Transactional(readOnly = true)
    public ChannelCredentialResponse getCredential(UUID channelId) {
        throw new UnsupportedOperationException("Chưa code");
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ChannelProductResponse> getChannelProducts(UUID channelId, int page, int size) {
        throw new UnsupportedOperationException("Chưa code");
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, List<String>> getProductChannels(Collection<UUID> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<ChannelProduct> channelProducts = channelProductRepository.findByProductIdInAndMappingState(productIds, "ACTIVE");
        return channelProducts.stream()
                .filter(cp -> cp.getProduct() != null && cp.getChannel() != null)
                .collect(Collectors.groupingBy(
                        cp -> cp.getProduct().getId(),
                        Collectors.mapping(
                                cp -> cp.getChannel().getPlatform().name(),
                                Collectors.collectingAndThen(
                                        Collectors.toList(),
                                        list -> list.stream().distinct().collect(Collectors.toList())
                                )
                        )
                ));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, List<ChannelSyncResponse>> getProductChannelSyncs(Collection<UUID> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<ChannelProduct> channelProducts = channelProductRepository.findByProductIdInAndMappingState(productIds, "ACTIVE");
        return channelProducts.stream()
                .filter(cp -> cp.getProduct() != null && cp.getChannel() != null)
                .collect(Collectors.groupingBy(
                        cp -> cp.getProduct().getId(),
                        Collectors.mapping(
                                cp -> ChannelSyncResponse.builder()
                                        .platform(cp.getChannel().getPlatform().name())
                                        .syncStatus(cp.getSyncStatus())
                                        .lastSyncedAt(cp.getLastSyncedAt())
                                        .lastSyncError(cp.getLastSyncError())
                                        .build(),
                                Collectors.toList()
                        )
                ));
    }
}
