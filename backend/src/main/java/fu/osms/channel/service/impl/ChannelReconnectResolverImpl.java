package fu.osms.channel.service.impl;

import fu.osms.channel.entity.Channel;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.channel.service.ChannelReconnectResolver;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.sync.shopify.ShopifyShopDomainNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class ChannelReconnectResolverImpl implements ChannelReconnectResolver {

    private final ChannelRepository channelRepository;
    private final ShopifyShopDomainNormalizer shopDomainNormalizer;

    @Override
    public Channel resolveShopify(String shopHandle) {
        String normalized = shopDomainNormalizer.normalizeHandle(shopHandle);
        List<Channel> candidates = channelRepository.findShopifyCandidatesByHandle(normalized).stream()
                .filter(channel -> candidateShopHandle(channel).equals(normalized))
                .toList();
        return resolve(PlatformType.SHOPIFY, normalized, candidates,
                () -> Channel.builder().platform(PlatformType.SHOPIFY).displayName(normalized).build());
    }

    @Override
    public Channel resolveLazada(String accountId, String displayName) {
        List<Channel> candidates = channelRepository.findLazadaCandidatesByAccountId(accountId);
        return resolve(PlatformType.LAZADA, accountId, candidates,
                () -> Channel.builder().platform(PlatformType.LAZADA).displayName(displayName).build());
    }

    @Override
    public Channel resolveTikTok(String shopId, String openId, String accountId, String displayName) {
        List<Channel> candidates = channelRepository.findTikTokCandidatesByIdentity(
                blankToEmpty(shopId), blankToEmpty(openId), blankToEmpty(accountId));
        String identity = firstNonBlank(shopId, openId, accountId, displayName);
        return resolve(PlatformType.TIKTOK, identity, candidates,
                () -> Channel.builder().platform(PlatformType.TIKTOK).displayName(displayName).build());
    }

    private Channel resolve(PlatformType platform, String identity, List<Channel> candidates,
                            Supplier<Channel> newChannel) {
        if (candidates.size() > 1) {
            throw conflict(platform, identity, candidates);
        }
        if (candidates.size() == 1) {
            return candidates.get(0);
        }

        Channel proposed = newChannel.get();
        return channelRepository.findByPlatformAndDisplayName(platform, proposed.getDisplayName())
                .filter(channel -> channel.getDeletedAt() != null)
                .orElse(proposed);
    }

    private AppException conflict(PlatformType platform, String identity, List<Channel> candidates) {
        String ids = candidates.stream().map(Channel::getId).filter(Objects::nonNull)
                .map(String::valueOf).sorted().reduce((left, right) -> left + "," + right).orElse("");
        return new AppException(ErrorCode.CHANNEL_IDENTITY_CONFLICT,
                "Multiple " + platform + " channels match identity " + identity + ": " + ids);
    }

    private String candidateShopHandle(Channel channel) {
        Object stored = channel.getMetadata() == null ? null : channel.getMetadata().get("shopDomain");
        if (stored == null && channel.getMetadata() != null) {
            stored = channel.getMetadata().get("shop");
        }
        return shopDomainNormalizer.normalizeHandle(stored == null ? channel.getDisplayName() : stored.toString());
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "unknown";
    }
}
