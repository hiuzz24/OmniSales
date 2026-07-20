package fu.osms.channel.service.impl;

import fu.osms.channel.dto.request.ChannelRequest;
import fu.osms.channel.dto.response.ChannelProductResponse;
import fu.osms.channel.dto.response.ChannelResponse;
import fu.osms.channel.dto.response.ChannelSyncResponse;
import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelCredential;
import fu.osms.channel.mapper.ChannelMapper;
import fu.osms.channel.repository.ChannelCredentialRepository;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.channel.service.ChannelConnectionService;
import fu.osms.channel.service.ChannelProductQueryService;
import fu.osms.channel.service.ChannelResponseService;
import fu.osms.channel.service.ChannelService;
import fu.osms.common.dto.PageResponse;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.sync.dto.shopify.WebhookRegistrationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChannelServiceImpl implements ChannelService {

    private final ChannelRepository channelRepository;
    private final ChannelCredentialRepository credentialRepository;
    private final ChannelMapper channelMapper;
    private final ChannelConnectionService connectionService;
    private final ChannelProductQueryService productQueryService;
    private final ChannelResponseService responseService;

    @Override
    @Transactional
    public ChannelResponse create(ChannelRequest request) {
        if (channelRepository.existsByPlatformAndDisplayName(request.getPlatform(), request.getDisplayName())) {
            throw new AppException(ErrorCode.CHANNEL_ALREADY_EXISTS);
        }
        Channel channel = channelMapper.toEntity(request);
        channel.setStatus("CONNECTED");
        replaceMetadata(channel, request.getMetadata());
        channelRepository.save(channel);
        credentialRepository.save(ChannelCredential.builder()
                .channel(channel)
                .connectionState("CONNECTED")
                .build());
        return responseService.toResponse(channel);
    }

    @Override
    @Transactional(readOnly = true)
    public ChannelResponse getById(UUID id) {
        Channel channel = activeChannel(id);
        responseService.enrichStats(channel);
        return responseService.toResponse(channel);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChannelResponse> getAll() {
        return channelRepository.findByDeletedAtIsNull().stream()
                .peek(responseService::enrichStats)
                .map(responseService::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public ChannelResponse update(UUID id, ChannelRequest request) {
        Channel channel = activeChannel(id);
        channel.setDisplayName(request.getDisplayName());
        channel.setCommissionRate(request.getCommissionRate());
        mergeMetadata(channel, request.getMetadata());
        if (request.getSyncEnabled() != null) {
            channel.setSyncEnabled(request.getSyncEnabled());
        }
        channelRepository.save(channel);
        return responseService.toResponse(channel);
    }

    @Override
    public void delete(UUID id) {
        connectionService.disconnect(id);
    }

    @Override
    public PageResponse<ChannelProductResponse> getChannelProducts(UUID channelId, int page, int size) {
        return productQueryService.getChannelProducts(channelId, page, size);
    }

    @Override
    public Map<UUID, List<String>> getProductChannels(Collection<UUID> productIds) {
        return productQueryService.getProductChannels(productIds);
    }

    @Override
    public Map<UUID, List<UUID>> getProductChannelIds(Collection<UUID> productIds) {
        return productQueryService.getProductChannelIds(productIds);
    }

    @Override
    public Map<UUID, List<ChannelSyncResponse>> getProductChannelSyncs(Collection<UUID> productIds) {
        return productQueryService.getProductChannelSyncs(productIds);
    }

    @Override
    public ChannelResponse connectShopify(String shop, String accessToken) {
        return connectionService.connectShopify(shop, accessToken);
    }

    @Override
    public void registerShopifyWebhooks(String shop, String accessToken, UUID channelId) {
        connectionService.registerShopifyWebhooks(shop, accessToken, channelId);
    }

    @Override
    public ChannelResponse connectLazada(String accessToken, String refreshToken, int expiresIn,
                                         int refreshExpiresIn, String accountId, String accountName) {
        return connectionService.connectLazada(accessToken, refreshToken, expiresIn,
                refreshExpiresIn, accountId, accountName);
    }

    @Override
    public ChannelResponse connectTikTok(String accessToken, String refreshToken, int expiresIn,
                                         int refreshExpiresIn, String accountId, String accountName,
                                         Map<String, Object> metadata) {
        return connectionService.connectTikTok(accessToken, refreshToken, expiresIn,
                refreshExpiresIn, accountId, accountName, metadata);
    }

    @Override
    public void updateShopifyWebhookMetadata(UUID channelId, WebhookRegistrationResult result) {
        connectionService.updateShopifyWebhookMetadata(channelId, result);
    }

    private Channel activeChannel(UUID id) {
        return channelRepository.findById(id)
                .filter(channel -> channel.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.CHANNEL_NOT_FOUND));
    }

    private void replaceMetadata(Channel channel, Map<String, Object> requestMetadata) {
        Map<String, Object> metadata = new HashMap<>();
        applyMetadataChanges(metadata, requestMetadata);
        channel.setMetadata(metadata);
    }

    private void mergeMetadata(Channel channel, Map<String, Object> requestMetadata) {
        Map<String, Object> metadata = channel.getMetadata() == null
                ? new HashMap<>() : new HashMap<>(channel.getMetadata());
        applyMetadataChanges(metadata, requestMetadata);
        channel.setMetadata(metadata);
    }

    private void applyMetadataChanges(Map<String, Object> metadata, Map<String, Object> changes) {
        if (changes == null) {
            return;
        }
        changes.forEach((key, value) -> {
            if (value == null || value instanceof String text && text.isBlank()) {
                metadata.remove(key);
            } else {
                metadata.put(key, value);
            }
        });
    }
}
