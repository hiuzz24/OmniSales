package fu.osms.sync.tiktok.returning.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.channel.entity.Channel;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.sync.tiktok.TikTokAuthorizedApiClient;
import fu.osms.sync.tiktok.returning.TikTokReturnApiService;
import fu.osms.sync.webhook.WebhookPayloadUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class TikTokReturnApiServiceImpl implements TikTokReturnApiService {

    private static final String SEARCH_RETURNS_PATH = "/return_refund/202309/returns/search";

    private final TikTokAuthorizedApiClient apiClient;
    private final ChannelRepository channelRepository;
    private final ObjectMapper objectMapper;

    @Override
    public Map<String, Object> getReturn(UUID channelId, String externalReturnId) {
        if (externalReturnId == null || externalReturnId.isBlank()) {
            throw new AppException(ErrorCode.ORDER_RETURN_ITEM_IDENTITY_MISSING,
                    "TikTok return ID is missing");
        }
        Map<String, Object> lastData = Map.of();
        for (int attempt = 1; attempt <= 3; attempt++) {
            SearchResult result = searchReturn(channelId, externalReturnId);
            if (result.returnData() != null) {
                return result.returnData();
            }
            lastData = result.responseData();
            if (attempt < 3) {
                waitForSearchIndex();
            }
        }
        throw new IllegalStateException(
                "TikTok search returns did not contain return ID " + externalReturnId
                        + "; dataKeys=" + lastData.keySet());
    }

    private SearchResult searchReturn(UUID channelId, String externalReturnId) {
        String response = apiClient.executePost(
                channelId,
                SEARCH_RETURNS_PATH,
                Map.of(
                        "shop_cipher", shopCipher(channelId),
                        "page_size", "10"),
                json(Map.of("return_ids", List.of(externalReturnId))));

        Map<String, Object> root = WebhookPayloadUtils.parseObject(
                response, "TikTok search returns response is invalid");
        String code = WebhookPayloadUtils.text(root.get("code"));
        if (code != null && !"0".equals(code)) {
            throw new IllegalStateException("TikTok search returns failed: "
                    + WebhookPayloadUtils.text(root.get("message")));
        }
        Map<String, Object> data = WebhookPayloadUtils.copyMap(root.get("data"));
        Object rawReturns = WebhookPayloadUtils.firstPresent(
                data, "return_orders", "returns", "return_list", "orders");
        if (rawReturns instanceof Map<?, ?> singleReturn) {
            rawReturns = List.of(singleReturn);
        }
        if (!(rawReturns instanceof List<?> returns)) {
            return new SearchResult(null, data);
        }
        Map<String, Object> match = returns.stream()
                .filter(Map.class::isInstance)
                .map(WebhookPayloadUtils::copyMap)
                .filter(item -> externalReturnId.equals(WebhookPayloadUtils.text(
                        WebhookPayloadUtils.firstPresent(item, "return_id", "id"))))
                .findFirst()
                .orElse(null);
        return new SearchResult(match, data);
    }

    private void waitForSearchIndex() {
        try {
            TimeUnit.SECONDS.sleep(1);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for TikTok return detail", exception);
        }
    }

    private String shopCipher(UUID channelId) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new AppException(ErrorCode.CHANNEL_NOT_FOUND));
        String value = WebhookPayloadUtils.text(channel.getMetadata() == null
                ? null : channel.getMetadata().get("shopCipher"));
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("TikTok shopCipher is missing");
        }
        return value;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot serialize TikTok search returns request", exception);
        }
    }

    private record SearchResult(
            Map<String, Object> returnData,
            Map<String, Object> responseData
    ) {
    }
}
