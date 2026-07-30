package fu.osms.sync.shopify.returning.impl;

import fu.osms.common.enums.PlatformType;
import fu.osms.orderreturn.model.OrderReturnSnapshot;
import fu.osms.orderreturn.model.ReturnActionContext;
import fu.osms.orderreturn.model.ReturnPlatformActionResult;
import fu.osms.orderreturn.service.OrderReturnPlatformGateway;
import fu.osms.sync.shopify.returning.ShopifyReturnGraphQlClient;
import fu.osms.sync.shopify.returning.ShopifyReturnSnapshotMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class ShopifyOrderReturnGateway implements OrderReturnPlatformGateway {

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
    public ReturnPlatformActionResult reject(ReturnActionContext context, String reason) {
        return result(client.decline(context.channelId(), context.externalReturnId(), reason), context, true);
    }

    @Override
    public ReturnPlatformActionResult process(ReturnActionContext context) {
        return result(client.process(context.channelId(), context.externalReturnId(), context), context, true);
    }

    @Override
    public ReturnPlatformActionResult check(ReturnActionContext context) {
        Map<String, Object> remote = client.getReturn(context.channelId(), context.externalReturnId());
        OrderReturnSnapshot snapshot = mapper.map(remote, null, false);
        String status = snapshot.platformStatus() == null ? "" : snapshot.platformStatus().toUpperCase();
        boolean applied = switch (context.action()) {
            case APPROVE -> status.equals("OPEN") || status.equals("CLOSED");
            case REJECT -> status.equals("DECLINED") || status.equals("CANCELED");
            case PROCESS -> status.equals("CLOSED");
        };
        return new ReturnPlatformActionResult(snapshot, applied);
    }

    private ReturnPlatformActionResult result(Map<String, Object> remote,
                                              ReturnActionContext context,
                                              boolean applied) {
        return new ReturnPlatformActionResult(mapper.map(remote, null, false), applied);
    }
}
