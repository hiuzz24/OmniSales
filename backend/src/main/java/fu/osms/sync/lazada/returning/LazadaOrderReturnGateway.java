package fu.osms.sync.lazada.returning;

import fu.osms.common.enums.PlatformType;
import fu.osms.orderreturn.enums.ReturnAction;
import fu.osms.orderreturn.model.OrderReturnSnapshot;
import fu.osms.orderreturn.model.ReturnActionContext;
import fu.osms.orderreturn.model.ReturnPlatformActionResult;
import fu.osms.orderreturn.model.ReturnRejectCommand;
import fu.osms.orderreturn.service.OrderReturnPlatformGateway;
import fu.osms.sync.lazada.service.LazadaAuthorizedApiClient;
import fu.osms.sync.webhook.WebhookPayloadUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class LazadaOrderReturnGateway implements OrderReturnPlatformGateway {

    private static final String DETAIL_PATH = "/order/reverse/get";
    private static final String ACTION_PATH = "/order/reverse/return/update";

    private final LazadaAuthorizedApiClient apiClient;
    private final LazadaReturnSnapshotMapper mapper;

    @Override
    public PlatformType platform() {
        return PlatformType.LAZADA;
    }

    @Override
    public ReturnPlatformActionResult approve(ReturnActionContext context) {
        return action(context, "APPROVE", null);
    }

    @Override
    public ReturnPlatformActionResult reject(ReturnActionContext context, ReturnRejectCommand command) {
        String comment = command == null ? null : command.comment();
        if (comment == null || comment.isBlank()) {
            throw new fu.osms.common.exception.AppException(
                    fu.osms.common.exception.ErrorCode.INVALID_REQUEST,
                    "Vui lòng nhập lý do từ chối Lazada");
        }
        return action(context, "REJECT", comment.trim());
    }

    @Override
    public ReturnPlatformActionResult process(ReturnActionContext context) {
        return action(context, "CONFIRM_RECEIVED", null);
    }

    @Override
    public ReturnPlatformActionResult check(ReturnActionContext context) {
        OrderReturnSnapshot snapshot = detail(context, null);
        String status = snapshot.platformStatus() == null ? "" : snapshot.platformStatus().toUpperCase();
        boolean applied = switch (context.action()) {
            case APPROVE -> status.contains("APPROV") || status.contains("RETURN") || status.contains("COMPLET");
            case REJECT -> status.contains("REJECT") || status.contains("CANCEL");
            case PROCESS -> snapshot.refundConfirmed();
        };
        return new ReturnPlatformActionResult(snapshot, applied);
    }

    private ReturnPlatformActionResult action(ReturnActionContext context, String action, String reason) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("reverse_order_id", context.externalReturnId());
        params.put("action", action);
        params.put("request_id", context.requestId().toString());
        if (reason != null && !reason.isBlank()) params.put("reason", reason);
        parseSuccess(apiClient.executePost(context.channelId(), ACTION_PATH, params), "Lazada return action");
        return new ReturnPlatformActionResult(detail(context, null), true);
    }

    private OrderReturnSnapshot detail(ReturnActionContext context, String webhookEventId) {
        String response = apiClient.executeGet(context.channelId(), DETAIL_PATH,
                Map.of("reverse_order_id", context.externalReturnId()));
        return mapper.map(parseSuccess(response, "Lazada return detail"), webhookEventId);
    }

    private Map<String, Object> parseSuccess(String response, String operation) {
        Map<String, Object> root = WebhookPayloadUtils.parseObject(response, operation + " response is invalid");
        String code = WebhookPayloadUtils.text(root.get("code"));
        if (code != null && !"0".equals(code)) {
            throw new IllegalStateException(operation + " failed: "
                    + WebhookPayloadUtils.text(WebhookPayloadUtils.firstPresent(root, "message", "msg")));
        }
        return root;
    }
}
