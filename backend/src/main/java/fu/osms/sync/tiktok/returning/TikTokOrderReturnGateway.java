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
import fu.osms.orderreturn.model.ReturnRejectCommand;
import fu.osms.orderreturn.model.ReturnRejectOptions;
import fu.osms.orderreturn.service.OrderReturnPlatformGateway;
import fu.osms.sync.tiktok.TikTokAuthorizedApiClient;
import fu.osms.sync.webhook.WebhookPayloadUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
        return action(context, "APPROVE_RETURN", null, null);
    }

    @Override
    public ReturnPlatformActionResult reject(ReturnActionContext context, ReturnRejectCommand command) {
        ReturnRejectOptions options = rejectOptions(context);
        if (options.unavailableReason() != null) {
            throw new AppException(ErrorCode.CONFLICT, options.unavailableReason());
        }
        String reasonCode = command == null ? null : command.reasonCode();
        Set<String> validCodes = options.options().stream()
                .map(ReturnRejectOptions.Option::code)
                .collect(Collectors.toSet());
        if (reasonCode == null || !validCodes.contains(reasonCode)) {
            throw new AppException(ErrorCode.CONFLICT,
                    "Lý do từ chối TikTok đã hết hiệu lực. Vui lòng tải lại danh sách lý do.");
        }
        return action(context, "REJECT_RETURN", reasonCode, command.comment());
    }

    @Override
    public ReturnRejectOptions rejectOptions(ReturnActionContext context) {
        return returnApiService.getRejectOptions(context.channelId(), context.externalReturnId());
    }

    @Override
    public ReturnPlatformActionResult process(ReturnActionContext context) {
        OrderReturnSnapshot remote = detail(context, null);
        String status = normalizedStatus(remote);
        if ("REQUEST_SUCCESS".equals(status) || "RETURN_OR_REFUND_REQUEST_COMPLETE".equals(status)) {
            return new ReturnPlatformActionResult(remote, true);
        }
        if (!"BUYER_SHIPPED_ITEM".equals(status)) {
            throw new AppException(ErrorCode.ORDER_RETURN_PLATFORM_NOT_READY,
                    "TikTok chưa xác nhận khách đã gửi hàng; không thể xác nhận nhận kiện");
        }
        boolean incomplete = context.items().stream().anyMatch(item ->
                item.receivedQuantity() == null
                        || item.receivedQuantity() != item.approvedQuantity()
                        || item.missingQuantity() == null
                        || item.missingQuantity() != 0);
        if (incomplete) {
            throw new AppException(ErrorCode.ORDER_RETURN_PARTIAL_REQUIRES_MANUAL);
        }
        return action(context, "APPROVE_RECEIVED_PACKAGE", null, null);
    }

    @Override
    public void validateInspection(ReturnActionContext context) {
        String status = normalizedStatus(detail(context, null));
        if (!"BUYER_SHIPPED_ITEM".equals(status)) {
            String message = "AWAITING_BUYER_SHIP".equals(status)
                    ? "TikTok đang chờ khách gửi hàng."
                    : "TikTok chưa ở trạng thái cho phép nhận và kiểm hàng: " + status;
            throw new AppException(ErrorCode.ORDER_RETURN_PLATFORM_NOT_READY, message);
        }
    }

    @Override
    public ReturnPlatformActionResult refresh(ReturnActionContext context) {
        return new ReturnPlatformActionResult(detail(context, null), true);
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

    private ReturnPlatformActionResult action(ReturnActionContext context,
                                              String decision,
                                              String reasonCode,
                                              String comment) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("decision", decision);
        body.put("idempotency_key", context.requestId().toString());
        if (reasonCode != null && !reasonCode.isBlank()) body.put("reject_reason", reasonCode);
        if (comment != null && !comment.isBlank()) body.put("comment", comment.trim());
        String endpointAction = decision.startsWith("REJECT") ? "reject" : "approve";
        String response = apiClient.executePost(
                context.channelId(),
                "/return_refund/202309/returns/" + context.externalReturnId() + "/" + endpointAction,
                Map.of("shop_cipher", shopCipher(context.channelId())),
                json(body));
        parseSuccess(response, "TikTok return action");
        return new ReturnPlatformActionResult(detail(context, null), true);
    }

    private String normalizedStatus(OrderReturnSnapshot snapshot) {
        return snapshot.platformStatus() == null
                ? ""
                : snapshot.platformStatus().trim().toUpperCase(java.util.Locale.ROOT);
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
            String message = WebhookPayloadUtils.text(root.get("message"));
            if ("25011013".equals(code)) {
                throw new AppException(ErrorCode.ORDER_RETURN_PLATFORM_NOT_READY,
                        "TikTok chưa cho phép xác nhận nhận kiện ở trạng thái hiện tại");
            }
            throw new IllegalStateException(operation + " failed: " + message);
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
