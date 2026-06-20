package fu.osms.channel.service.impl;

import fu.osms.channel.dto.request.ChannelRequest;
import fu.osms.channel.dto.request.CreateManualChannelRequest;
import fu.osms.channel.dto.response.ChannelCredentialResponse;
import fu.osms.channel.dto.response.ChannelProductResponse;
import fu.osms.channel.dto.response.ChannelResponse;
import fu.osms.channel.dto.response.ChannelSyncResponse;
import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelCredential;
import fu.osms.channel.entity.ChannelProduct;
import fu.osms.channel.mapper.ChannelCredentialMapper;
import fu.osms.channel.mapper.ChannelMapper;
import fu.osms.channel.mapper.ChannelProductMapper;
import fu.osms.channel.repository.ChannelCredentialRepository;
import fu.osms.channel.repository.ChannelProductRepository;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.channel.service.ChannelService;
import fu.osms.common.dto.PageResponse;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.common.enums.PlatformType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
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
        if (channelRepository.existsByPlatformAndDisplayName(request.getPlatform(), request.getDisplayName())) {
            throw new AppException(ErrorCode.CHANNEL_ALREADY_EXISTS);
        }

        Channel channel = channelMapper.toEntity(request);
        channel.setStatus("CONNECTED");
        channelRepository.save(channel);

        ChannelCredential credential = ChannelCredential.builder()
                .channel(channel)
                .accessToken(null)
                .refreshToken(null)
                .connectionState("CONNECTED")
                .build();
        credentialRepository.save(credential);

        return channelMapper.toResponse(channel);
    }

    @Override
    @Transactional
    public ChannelResponse createManual(CreateManualChannelRequest request) {
        if (channelRepository.existsByPlatformAndDisplayName(request.getPlatform(), request.getDisplayName())) {
            throw new AppException(ErrorCode.CHANNEL_ALREADY_EXISTS);
        }

        Channel channel = channelMapper.toEntity(request);
        channel.setStatus("CONNECTED");
        channelRepository.save(channel);

        ChannelCredential credential = ChannelCredential.builder()
                .channel(channel)
                .accessToken(request.getAccessToken())
                .refreshToken(request.getRefreshToken())
                .connectionState("CONNECTED")
                .build();
        credentialRepository.save(credential);

        return channelMapper.toResponse(channel);
    }

    @Override
    @Transactional(readOnly = true)
    public ChannelResponse getById(UUID id) {
        Channel channel = channelRepository.findById(id)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.CHANNEL_NOT_FOUND));
        return channelMapper.toResponse(channel);
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
        Channel channel = channelRepository.findById(id)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.CHANNEL_NOT_FOUND));

        channel.setDisplayName(request.getDisplayName());
        channel.setCommissionRate(request.getCommissionRate());
        if (request.getMetadata() != null) {
            channel.setMetadata(request.getMetadata());
        }
        if (request.getSyncEnabled() != null) {
            channel.setSyncEnabled(request.getSyncEnabled());
        }
        channelRepository.save(channel);



        return channelMapper.toResponse(channel);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Channel channel = channelRepository.findById(id)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.CHANNEL_NOT_FOUND));

        channel.setDeletedAt(OffsetDateTime.now());
        channelRepository.save(channel);

        credentialRepository.findByChannelId(id).ifPresent(cred -> {
            cred.setConnectionState("DISCONNECTED");
            credentialRepository.save(cred);
        });
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

    @Override
    @Transactional
    public ChannelResponse connectShopify(String shop, String accessToken) {
        String normalizedShop = shop.endsWith(".myshopify.com")
                ? shop.substring(0, shop.length() - ".myshopify.com".length())
                : shop;

        log.info("[ChannelService] connectShopify — shop={}", normalizedShop);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("shop", normalizedShop);

        Channel channel = channelRepository
                .findActiveShopifyByShopDomain(normalizedShop)
                .orElseGet(() -> Channel.builder()
                        .platform(PlatformType.SHOPIFY)
                        .displayName(normalizedShop)
                        .build());

        channel.setStatus("CONNECTED");
        channel.setMetadata(metadata);
        channelRepository.save(channel);

        ChannelCredential credential = credentialRepository
                .findByChannelId(channel.getId())
                .orElseGet(() -> ChannelCredential.builder().channel(channel).build());

        credential.setAccessToken(accessToken);
        credential.setConnectionState("CONNECTED");
        credentialRepository.save(credential);

        log.info("[ChannelService] connectShopify success — channelId={}", channel.getId());
        return channelMapper.toResponse(channel);
    }
}
