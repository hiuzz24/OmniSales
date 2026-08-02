package fu.osms.sync.shopify.returning.impl;

import fu.osms.common.enums.PlatformType;
import fu.osms.orderreturn.model.OrderReturnSnapshot;
import fu.osms.orderreturn.model.ReturnActionContext;
import fu.osms.orderreturn.model.ReturnPlatformActionResult;
import fu.osms.orderreturn.model.ReturnRejectCommand;
import fu.osms.orderreturn.model.ReturnRejectOptions;
import fu.osms.orderreturn.service.OrderReturnPlatformGateway;
import fu.osms.sync.shopify.returning.ShopifyReturnGraphQlClient;
import fu.osms.sync.shopify.returning.ShopifyReturnSnapshotMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ShopifyOrderReturnGateway implements OrderReturnPlatformGateway {

    private static final Set<String> DECLINE_REASONS = Set.of(
            "FINAL_SALE", "RETURN_PERIOD_ENDED", "OTHER");

    private final ShopifyReturnGraphQlClient client;
    private final ShopifyReturnSnapshotMapper mapper;

    @Override
    public PlatformType platform() {
        return PlatformType.SHOPIFY;
    }

    @Override
    public ReturnPlatformActionResult approve(ReturnActionContext context) {
        return result(client.approve(context.channelId(), context.externalReturnId()), context, true);
    }

    @Override
    public ReturnPlatformActionResult reject(ReturnActionContext context, ReturnRejectCommand command) {
        String reasonCode = command == null || command.reasonCode() == null
                ? null
                : command.reasonCode().trim().toUpperCase(Locale.ROOT);
        if (!DECLINE_REASONS.contains(reasonCode)) {
            throw new fu.osms.common.exception.AppException(
                    fu.osms.common.exception.ErrorCode.INVALID_REQUEST,
                    "Lý do từ chối Shopify không hợp lệ");
        }
        return result(client.decline(
                context.channelId(), context.externalReturnId(), reasonCode, command.comment()), context, true);
    }

    @Override
    public ReturnRejectOptions rejectOptions(ReturnActionContext context) {
        return new ReturnRejectOptions(true, true, List.of(
                new ReturnRejectOptions.Option("FINAL_SALE", "Sản phẩm không áp dụng trả hàng"),
                new ReturnRejectOptions.Option("RETURN_PERIOD_ENDED", "Đã hết thời hạn trả hàng"),
                new ReturnRejectOptions.Option("OTHER", "Lý do khác")
        ), null);
    }

    @Override
    public ReturnPlatformActionResult process(ReturnActionContext context) {
        Map<String, Object> remote = client.process(
                context.channelId(), context.externalReturnId(), context);
        return processResult(mapper.map(remote, null));
    }

    @Override
    public ReturnPlatformActionResult check(ReturnActionContext context) {
        Map<String, Object> remote = client.getReturn(context.channelId(), context.externalReturnId());
        OrderReturnSnapshot snapshot = mapper.map(remote, null);
        String status = snapshot.platformStatus() == null ? "" : snapshot.platformStatus().toUpperCase();
        boolean applied = switch (context.action()) {
            case APPROVE -> status.equals("OPEN") || status.equals("CLOSED");
            case REJECT -> status.equals("DECLINED") || status.equals("CANCELED");
            case PROCESS -> "CLOSED".equals(status);
        };
        if (applied) {
            return new ReturnPlatformActionResult(snapshot, true, false, null);
        }
        if (context.action() == fu.osms.orderreturn.enums.ReturnAction.PROCESS) {
            return processResult(snapshot);
        }
        return new ReturnPlatformActionResult(snapshot, false, true,
                "Shopify has not applied the return action");
    }

    @Override
    public ReturnPlatformActionResult refresh(ReturnActionContext context) {
        Map<String, Object> remote = client.getReturn(context.channelId(), context.externalReturnId());
        return new ReturnPlatformActionResult(mapper.map(remote, null), true);
    }

    private ReturnPlatformActionResult result(Map<String, Object> remote,
                                              ReturnActionContext context,
                                              boolean applied) {
        return new ReturnPlatformActionResult(mapper.map(remote, null), applied);
    }

    private ReturnPlatformActionResult processResult(OrderReturnSnapshot snapshot) {
        String status = snapshot.platformStatus() == null
                ? ""
                : snapshot.platformStatus().trim().toUpperCase();
        if ("CLOSED".equals(status)) {
            return new ReturnPlatformActionResult(snapshot, true, false, null);
        }
        Map<String, Object> metadata = snapshot.metadata() == null
                ? Map.of()
                : snapshot.metadata();
        boolean hasSideEffects = Boolean.TRUE.equals(metadata.get("shopifyProcessEvidence"));
        if ("OPEN".equals(status) && !hasSideEffects) {
            return new ReturnPlatformActionResult(snapshot, false, true,
                    "Shopify return is still OPEN and has no processing side effects");
        }
        return ReturnPlatformActionResult.indeterminate(snapshot,
                "Shopify may have partially processed the return; check Shopify before retrying");
    }
}
