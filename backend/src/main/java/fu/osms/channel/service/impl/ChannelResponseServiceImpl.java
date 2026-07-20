package fu.osms.channel.service.impl;

import fu.osms.channel.dto.response.ChannelResponse;
import fu.osms.channel.entity.Channel;
import fu.osms.channel.mapper.ChannelMapper;
import fu.osms.channel.repository.ChannelCredentialRepository;
import fu.osms.channel.repository.ChannelProductRepository;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.channel.service.ChannelResponseService;
import fu.osms.common.enums.PlatformType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChannelResponseServiceImpl implements ChannelResponseService {

    private final ChannelMapper channelMapper;
    private final ChannelCredentialRepository credentialRepository;
    private final ChannelProductRepository channelProductRepository;
    private final ChannelProductVariantRepository channelProductVariantRepository;

    @Value("${lazada.webhook-callback-url:}")
    private String lazadaWebhookCallbackUrl;

    @Override
    public void enrichStats(Channel channel) {
        Map<String, Object> metadata = channel.getMetadata() == null
                ? new HashMap<>()
                : new HashMap<>(channel.getMetadata());
        metadata.put("productCount", channelProductRepository.countByChannelIdAndMappingState(channel.getId(), "ACTIVE"));
        metadata.put("skuVariantCount", channelProductVariantRepository.countActiveByChannelId(channel.getId()));
        metadata.putIfAbsent("warehouseCount", 0);
        if (channel.getPlatform() == PlatformType.LAZADA) {
            applyLazadaWebhookMetadata(metadata);
        }
        channel.setMetadata(metadata);
    }

    @Override
    public ChannelResponse toResponse(Channel channel) {
        ChannelResponse response = channelMapper.toResponse(channel);
        credentialRepository.findByChannelId(channel.getId()).ifPresent(credential -> {
            response.setConnectionState(credential.getConnectionState());
            response.setTokenExpiresAt(credential.getTokenExpiresAt());
            response.setRefreshTokenExpiresAt(credential.getRefreshTokenExpiresAt());
            response.setRefreshError(credential.getRefreshError());
        });
        return response;
    }

    private void applyLazadaWebhookMetadata(Map<String, Object> metadata) {
        String callbackUrl = configuredLazadaWebhookCallbackUrl();
        metadata.put("webhookCallbackUrl", callbackUrl);
        metadata.put("webhookRegistrationStatus", callbackUrl.isBlank()
                ? "MISSING_CALLBACK_URL"
                : "MANUAL_CONFIGURATION_REQUIRED");
        metadata.put("webhookRegistrationNote",
                "Configure this URL in Lazada Open Platform Push Mechanism and subscribe product/stock messages.");
    }

    private String configuredLazadaWebhookCallbackUrl() {
        return lazadaWebhookCallbackUrl == null ? "" : lazadaWebhookCallbackUrl.trim();
    }
}
