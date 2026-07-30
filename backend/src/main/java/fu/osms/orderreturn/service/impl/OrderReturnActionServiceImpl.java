package fu.osms.orderreturn.service.impl;

import fu.osms.channel.entity.Channel;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.orderreturn.enums.ReturnAction;
import fu.osms.orderreturn.model.ReturnActionContext;
import fu.osms.orderreturn.model.ReturnPlatformActionResult;
import fu.osms.orderreturn.service.OrderReturnActionService;
import fu.osms.orderreturn.service.OrderReturnPersistenceService;
import fu.osms.orderreturn.service.OrderReturnPlatformGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderReturnActionServiceImpl implements OrderReturnActionService {

    private final OrderReturnActionStateService stateService;
    private final List<OrderReturnPlatformGateway> gateways;
    private final ChannelRepository channelRepository;
    private final OrderReturnPersistenceService persistenceService;

    @Override
    public void execute(UUID returnId, ReturnAction action, String reason) {
        ReturnActionContext context = stateService.beginNew(returnId, action);
        call(context, reason, false);
    }

    @Override
    public void check(UUID returnId) {
        ReturnActionContext context = stateService.contextForCheck(returnId);
        try {
            ReturnPlatformActionResult result = gateway(context.platform()).check(context);
            if (result.applied()) {
                persist(context, result);
                stateService.markIdle(returnId);
            } else {
                stateService.markFailed(returnId, "Platform has not applied the action");
            }
        } catch (RuntimeException exception) {
            stateService.markUnknown(returnId, rootMessage(exception));
        }
    }

    @Override
    public void retry(UUID returnId) {
        ReturnActionContext context = stateService.beginRetry(returnId);
        try {
            ReturnPlatformActionResult checked = gateway(context.platform()).check(context);
            if (checked.applied()) {
                persist(context, checked);
                stateService.markIdle(returnId);
                return;
            }
        } catch (RuntimeException exception) {
            if (isUnknown(exception)) {
                stateService.markUnknown(returnId, rootMessage(exception));
                return;
            }
        }
        call(context, null, true);
    }

    private void call(ReturnActionContext context, String reason, boolean retry) {
        try {
            OrderReturnPlatformGateway gateway = gateway(context.platform());
            ReturnPlatformActionResult result = switch (context.action()) {
                case APPROVE -> gateway.approve(context);
                case REJECT -> gateway.reject(context, reason);
                case PROCESS -> gateway.process(context);
            };
            persist(context, result);
            stateService.markIdle(context.returnId());
        } catch (RuntimeException exception) {
            if (isUnknown(exception)) {
                stateService.markUnknown(context.returnId(), rootMessage(exception));
            } else {
                stateService.markFailed(context.returnId(),
                        (retry ? "Retry failed: " : "") + rootMessage(exception));
            }
        }
    }

    private void persist(ReturnActionContext context, ReturnPlatformActionResult result) {
        if (result.snapshot() == null) return;
        Channel channel = channelRepository.findById(context.channelId())
                .orElseThrow(() -> new AppException(ErrorCode.CHANNEL_NOT_FOUND));
        persistenceService.upsert(channel, result.snapshot());
    }

    private OrderReturnPlatformGateway gateway(PlatformType platform) {
        Map<PlatformType, OrderReturnPlatformGateway> byPlatform = new EnumMap<>(PlatformType.class);
        gateways.forEach(gateway -> byPlatform.put(gateway.platform(), gateway));
        OrderReturnPlatformGateway result = byPlatform.get(platform);
        if (result == null) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Return action is not supported for " + platform);
        }
        return result;
    }

    private boolean isUnknown(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ResourceAccessException || current instanceof SocketTimeoutException) return true;
            current = current.getCause();
        }
        return false;
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
