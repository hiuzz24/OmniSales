package fu.osms.channel.service.impl;

import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelCredential;
import fu.osms.channel.repository.ChannelCredentialRepository;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.channel.service.ChannelConnectionValidator;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChannelConnectionValidatorImpl implements ChannelConnectionValidator {

    private static final Set<PlatformType> OAUTH_PLATFORMS = Set.of(
            PlatformType.SHOPIFY, PlatformType.LAZADA, PlatformType.TIKTOK);

    private final ChannelRepository channelRepository;
    private final ChannelCredentialRepository credentialRepository;

    @Override
    /** Tải kênh và từ chối bản ghi không tồn tại, đã xóa hoặc đã ngắt kết nối. */
    public Channel requireConnected(UUID channelId) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new AppException(ErrorCode.CHANNEL_NOT_FOUND));
        validateConnected(channel);
        return channel;
    }

    @Override
    /** Kiểm tra yêu cầu credential riêng của platform đối với kênh đã kết nối. */
    public void validateConnected(Channel channel) {
        if (channel.getDeletedAt() != null || !"CONNECTED".equals(channel.getStatus())) {
            throw new AppException(ErrorCode.CHANNEL_NOT_CONNECTED);
        }

        ChannelCredential credential = credentialRepository.findByChannelId(channel.getId())
                .orElseThrow(() -> new AppException(ErrorCode.CHANNEL_NOT_CONNECTED));
        if (!"CONNECTED".equals(credential.getConnectionState())) {
            throw new AppException(ErrorCode.CHANNEL_NOT_CONNECTED);
        }
        if (OAUTH_PLATFORMS.contains(channel.getPlatform())
                && !StringUtils.hasText(credential.getAccessToken())) {
            throw new AppException(ErrorCode.CHANNEL_NOT_CONNECTED,
                    "Channel access token is missing; reconnect the channel");
        }
    }
}
