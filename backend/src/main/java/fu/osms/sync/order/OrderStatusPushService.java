package fu.osms.sync.order;

import fu.osms.common.enums.PlatformType;
import fu.osms.order.entity.Order;
import fu.osms.order.dto.response.CancelReasonResponse;
import fu.osms.order.enums.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderStatusPushService {

    private final List<PlatformOrderStatusPusher> pushers;

    public OrderStatusPushResult push(Order order, OrderStatus targetStatus, OrderStatusPushContext context) {
        if (order.getChannel() == null || order.getPlatform() == null || order.getPlatform() == PlatformType.MANUAL) {
            return OrderStatusPushResult.skipped("Manual order or missing channel");
        }

        PlatformOrderStatusPusher pusher = pusherMap().get(order.getPlatform());
        if (pusher == null) {
            return OrderStatusPushResult.skipped("No status pusher for platform " + order.getPlatform());
        }

        try {
            return pusher.push(order, targetStatus, context != null ? context : OrderStatusPushContext.empty());
        } catch (Exception e) {
            log.warn("[OrderStatusPush] Failed platform={} orderId={} targetStatus={}: {}",
                    order.getPlatform(), order.getId(), targetStatus, e.getMessage());
            return OrderStatusPushResult.failed(e.getMessage());
        }
    }

    public List<CancelReasonResponse> getCancelReasons(Order order) {
        if (order.getChannel() == null || order.getPlatform() == null || order.getPlatform() == PlatformType.MANUAL) {
            return List.of();
        }

        PlatformOrderStatusPusher pusher = pusherMap().get(order.getPlatform());
        if (pusher == null) {
            return List.of();
        }
        return pusher.getCancelReasons(order);
    }

    private Map<PlatformType, PlatformOrderStatusPusher> pusherMap() {
        return pushers.stream()
                .collect(Collectors.toMap(PlatformOrderStatusPusher::getPlatform, Function.identity()));
    }
}
