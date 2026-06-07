package fu.osms.channel.service.impl;

import fu.osms.channel.dto.request.ChannelRequest;
import fu.osms.channel.dto.response.ChannelCredentialResponse;
import fu.osms.channel.dto.response.ChannelProductResponse;
import fu.osms.channel.dto.response.ChannelResponse;
import fu.osms.channel.entity.Channel;
import fu.osms.channel.mapper.ChannelCredentialMapper;
import fu.osms.channel.mapper.ChannelMapper;
import fu.osms.channel.mapper.ChannelProductMapper;
import fu.osms.channel.repository.ChannelCredentialRepository;
import fu.osms.channel.repository.ChannelProductRepository;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.channel.service.ChannelService;
import fu.osms.common.dto.PageResponse;
import fu.osms.shop.entity.Shop;
import fu.osms.shop.repository.ShopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChannelServiceImpl implements ChannelService {

    private final ChannelRepository channelRepository;
    private final ChannelCredentialRepository credentialRepository;
    private final ChannelProductRepository channelProductRepository;
    private final ShopRepository shopRepository;
    private final ChannelMapper channelMapper;
    private final ChannelCredentialMapper credentialMapper;
    private final ChannelProductMapper channelProductMapper;

    @Override
    @Transactional
    public ChannelResponse create(ChannelRequest request) {
        Shop shop = shopRepository.findById(request.getShopId())
                .orElseThrow(() -> new RuntimeException("Shop not found"));
        Channel channel = channelMapper.toEntity(request);
        channel.setShop(shop);
        channel.setStatus("PENDING");
        return channelMapper.toResponse(channelRepository.save(channel));
    }

    @Override
    @Transactional(readOnly = true)
    public ChannelResponse getById(UUID id) {
        return channelRepository.findById(id)
                .map(channelMapper::toResponse)
                .orElseThrow(() -> new RuntimeException("Sales channel not found: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChannelResponse> getByShopId(UUID shopId) {
        return channelRepository.findByShopIdAndDeletedAtIsNull(shopId)
                .stream().map(channelMapper::toResponse).toList();
    }

    @Override
    @Transactional
    public ChannelResponse update(UUID id, ChannelRequest request) {
        Channel channel = channelRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Sales channel not found: " + id));
        channelMapper.updateEntityFromRequest(request, channel);
        return channelMapper.toResponse(channelRepository.save(channel));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Channel channel = channelRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Sales channel not found: " + id));
        channel.setDeletedAt(OffsetDateTime.now());
        channelRepository.save(channel);
    }

    @Override
    @Transactional(readOnly = true)
    public ChannelCredentialResponse getCredential(UUID channelId) {
        return credentialRepository.findByChannelId(channelId)
                .map(credentialMapper::toResponse)
                .orElseThrow(() -> new RuntimeException("Credential not found for channel: " + channelId));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ChannelProductResponse> getChannelProducts(UUID channelId, int page, int size) {
        var pageResult = channelProductRepository.findByChannelId(channelId, PageRequest.of(page, size));
        return PageResponse.<ChannelProductResponse>builder()
                .content(pageResult.getContent().stream().map(channelProductMapper::toResponse).toList())
                .page(page).size(size)
                .totalElements(pageResult.getTotalElements())
                .totalPages(pageResult.getTotalPages())
                .first(pageResult.isFirst()).last(pageResult.isLast())
                .build();
    }
}
