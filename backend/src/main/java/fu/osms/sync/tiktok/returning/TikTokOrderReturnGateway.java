package fu.osms.sync.tiktok.returning;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.channel.entity.Channel;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.orderreturn.model.OrderReturnSnapshot;
import fu.osms.orderreturn.model.ReturnActionContext;
import fu.osms.orderreturn.model.ReturnPlatformActionResult;
import fu.osms.orderreturn.service.OrderReturnPlatformGateway;
import fu.osms.sync.tiktok.TikTokAuthorizedApiClient;
import fu.osms.sync.webhook.WebhookPayloadUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class TikTokOrderReturnGateway implements OrderReturnPlatformGateway {

    private final TikTokAuthorizedApiClient apiClient;
    private final ChannelRepository channelRepository;
    private final TikTokReturnSnapshotMapper mapper;
    private final TikTokReturnApiService returnApiService;
    private final ObjectMapper objectMapper;

    @Override
    public PlatformType platform() {
        return PlatformType.TIKTOK;
    }

    @Override
    public ReturnPlatformActionResult approve(ReturnActionContext context) {
        return action(context, "APPROVE_RETURN", null);
    }

    @Override
    public ReturnPlatformActionResult reject(ReturnActionContext context, String reason) {
        return action(context, "REJECT_RETURN", reason);
    }

    @Override
    public ReturnPlatformActionResult process(ReturnActionContext context) {
        return action(context, "APPROVE_RECEIVED_PACKAGE", null);
    }

    @Override
    public ReturnPlatformActionResult check(ReturnActionContext context) {
        OrderReturnSnapshot snapshot = detail(context, null);
        String status = snapshot.platformStatus() == null ? "" : snapshot.platformStatus().toUpperCase();
        boolean applied = switch (context.action()) {
            case APPROVE -> status.contains("APPROV") || status.contains("TRANSIT") || status.contains("COMPLET");
            case REJECT -> status.contains("REJECT") || status.contains("CANCEL");
            case PROCESS -> snapshot.refundConfirmed();
        };
        return new ReturnPlatformActionResult(snapshot, applied);
    }

    private ReturnPlatformActionResult action(ReturnActionContext context, String decision, String reason) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("decision", decision);
        body.put("idempotency_key", context.requestId().toString());
        if (reason != null && !reason.isBlank()) body.put("reject_reason", reason);
        String endpointAction = decision.startsWith("REJECT") ? "reject" : "approve";
        String response = apiClient.executePost(
                context.channelId(),
                "/return_refund/202309/returns/" + context.externalReturnId() + "/" + endpointAction,
                Map.of("shop_cipher", shopCipher(context.channelId())),
                json(body));
        parseSuccess(response, "TikTok return action");
        return new ReturnPlatformActionResult(detail(context, null), true);
    }

    private OrderReturnSnapshot detail(ReturnActionContext context, String webhookEventId) {
        return mapper.map(
                returnApiService.getReturn(context.channelId(), context.externalReturnId()),
                webhookEventId);
    }

    private String shopCipher(java.util.UUID channelId) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new AppException(ErrorCode.CHANNEL_NOT_FOUND));
        String value = WebhookPayloadUtils.text(channel.getMetadata() == null
                ? null : channel.getMetadata().get("shopCipher"));
        if (value == null || value.isBlank()) throw new IllegalStateException("TikTok shopCipher is missing");
        return value;
    }

    private Map<String, Object> parseSuccess(String response, String operation) {
        Map<String, Object> root = WebhookPayloadUtils.parseObject(response, operation + " response is invalid");
        String code = WebhookPayloadUtils.text(root.get("code"));
        if (code != null && !"0".equals(code)) {
            throw new IllegalStateException(operation + " failed: " + WebhookPayloadUtils.text(root.get("message")));
        }
        Map<String, Object> data = WebhookPayloadUtils.copyMap(root.get("data"));
        return data.isEmpty() ? root : data;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot serialize TikTok return action", exception);
        }
    }
}
