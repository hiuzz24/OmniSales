package fu.osms.orderreturn.service.impl;

import fu.osms.channel.entity.Channel;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.orderreturn.enums.ReturnAction;
import fu.osms.orderreturn.model.ReturnActionContext;
import fu.osms.orderreturn.model.ReturnPlatformActionResult;
import fu.osms.orderreturn.model.ReturnRejectCommand;
import fu.osms.orderreturn.model.ReturnRejectOptions;
import fu.osms.orderreturn.service.OrderReturnActionService;
import fu.osms.orderreturn.service.OrderReturnPersistenceService;
import fu.osms.orderreturn.service.OrderReturnPlatformGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderReturnActionServiceImpl implements OrderReturnActionService {

    private final OrderReturnActionStateService stateService;
    private final List<OrderReturnPlatformGateway> gateways;
    private final ChannelRepository channelRepository;
    private final OrderReturnPersistenceService persistenceService;

    /** Tạo chu kỳ action mới và gọi platform ngoài database transaction. */
    @Override
    public void execute(UUID returnId, ReturnAction action, ReturnRejectCommand rejectCommand) {
        ReturnActionContext context = stateService.beginNew(returnId, action, rejectCommand);
        call(context, false);
    }

    /** Lấy điều kiện từ chối mà không thay đổi trạng thái trả hàng. */
    @Override
    public ReturnRejectOptions getRejectOptions(UUID returnId) {
        ReturnActionContext context = stateService.contextForRejectOptions(returnId);
        return gateway(context.platform()).rejectOptions(context);
    }

    /** Đảm bảo trạng thái platform hiện tại cho phép kiểm hàng tại kho. */
    @Override
    public void validateInspection(UUID returnId) {
        ReturnActionContext context = stateService.contextForRefresh(returnId);
        try {
            gateway(context.platform()).validateInspection(context);
        } catch (RuntimeException exception) {
            logStage(context, "PRECHECK", exception);
            throw exception;
        }
    }

    /** Chỉ kéo và lưu snapshot trả hàng mới nhất từ platform. */
    @Override
    public void refresh(UUID returnId) {
        ReturnActionContext context = stateService.contextForRefresh(returnId);
        try {
            ReturnPlatformActionResult result = gateway(context.platform()).refresh(context);
            persist(context, result);
        } catch (RuntimeException exception) {
            logStage(context, "PERSIST_SNAPSHOT", exception);
            throw exception;
        }
    }

    /** Kiểm tra action UNKNOWN và phân loại đã áp dụng, được phép thử lại hoặc vẫn chưa rõ. */
    @Override
    public void check(UUID returnId) {
        ReturnActionContext context = stateService.contextForCheck(returnId);
        try {
            ReturnPlatformActionResult result = gateway(context.platform()).check(context);
            handleCheckedResult(context, result);
        } catch (RuntimeException exception) {
            logStage(context, "CALL_PLATFORM", exception);
            stateService.markUnknown(returnId, rootMessage(exception));
        }
    }

    /** Xác minh trạng thái từ xa trước khi gọi lại action platform thất bại gần nhất. */
    @Override
    public void retry(UUID returnId) {
        ReturnActionContext context = stateService.beginRetry(returnId);
        try {
            ReturnPlatformActionResult checked = gateway(context.platform()).check(context);
            if (checked.applied()) {
                persistAndFinish(context, checked);
                return;
            }
            if (!checked.retrySafe()) {
                persist(context, checked);
                stateService.markUnknown(returnId, message(checked,
                        "Platform may have partially applied the action"));
                return;
            }
        } catch (RuntimeException exception) {
            logStage(context, "CALL_PLATFORM", exception);
            if (isUnknown(exception)) {
                stateService.markUnknown(returnId, rootMessage(exception));
                return;
            }
        }
        call(context, true);
    }

    private void call(ReturnActionContext context, boolean retry) {
        ReturnPlatformActionResult result;
        try {
            OrderReturnPlatformGateway gateway = gateway(context.platform());
            result = switch (context.action()) {
                case APPROVE -> gateway.approve(context);
                case REJECT -> gateway.reject(context, context.rejectCommand());
                case PROCESS -> gateway.process(context);
            };
        } catch (RuntimeException exception) {
            logStage(context, "CALL_PLATFORM", exception);
            if (isUnknown(exception)) {
                stateService.markUnknown(context.returnId(), rootMessage(exception));
            } else {
                stateService.markFailed(context.returnId(),
                        (retry ? "Retry failed: " : "") + rootMessage(exception));
            }
            if (exception instanceof AppException appException
                    && appException.getErrorCode() == ErrorCode.CONFLICT) {
                throw appException;
            }
            return;
        }

        try {
            if (result.applied()) {
                persistAndFinish(context, result);
            } else if (result.retrySafe()) {
                persist(context, result);
                stateService.markFailed(context.returnId(), message(result,
                        "Platform has not applied the action"));
            } else {
                persist(context, result);
                stateService.markUnknown(context.returnId(), message(result,
                        "Platform may have partially applied the action"));
            }
        } catch (RuntimeException exception) {
            logStage(context, "PERSIST_SNAPSHOT", exception);
            stateService.markUnknown(context.returnId(),
                    "Platform succeeded but OSMS could not persist the result: " + rootMessage(exception));
        }
    }

    private void persistAndFinish(ReturnActionContext context, ReturnPlatformActionResult result) {
        persist(context, result);
        try {
            stateService.markIdle(context.returnId());
        } catch (RuntimeException exception) {
            logStage(context, "FINISH_ACTION", exception);
            try {
                stateService.markUnknown(context.returnId(),
                        "Đã lưu kết quả từ sàn nhưng chưa thể hoàn tất trạng thái action trong OSMS");
            } catch (RuntimeException stateException) {
                exception.addSuppressed(stateException);
                logStage(context, "FINISH_ACTION", stateException);
            }
        }
    }

    private void handleCheckedResult(ReturnActionContext context, ReturnPlatformActionResult result) {
        if (result.applied()) {
            persistAndFinish(context, result);
            return;
        }
        persist(context, result);
        if (result.retrySafe()) {
            stateService.markFailed(context.returnId(), message(result,
                    "Platform has not applied the action"));
        } else {
            stateService.markUnknown(context.returnId(), message(result,
                    "Platform may have partially applied the action"));
        }
    }

    private String message(ReturnPlatformActionResult result, String fallback) {
        return result.message() == null || result.message().isBlank()
                ? fallback
                : result.message();
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

    private void logStage(ReturnActionContext context, String stage, Throwable exception) {
        log.error("[OrderReturnAction] returnId={} actionRequestId={} platform={} stage={}",
                context.returnId(), context.requestId(), context.platform(), stage, exception);
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
