package fu.osms.messaging.handler;

import fu.osms.inventory.service.OrderStockDeliveryService;
import fu.osms.messaging.dto.OrderStockDeliveryLifecycleMessage;
import fu.osms.order.enums.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderStockDeliveryLifecycleHandler {

    private final OrderStockDeliveryService orderStockDeliveryService;

    /** Điều phối message đơn đã giao hoặc đã hủy tới service phiếu xuất idempotent. */
    public void handle(OrderStockDeliveryLifecycleMessage message) {
        log.info("[OrderStockDeliveryLifecycle] actionKey={} messageId={} orderId={} status={}",
                message.actionKey(), message.messageId(), message.orderId(), message.status());
        if (message.status() == OrderStatus.DELIVERED) {
            orderStockDeliveryService.completeForOrder(message.orderId());
        } else if (message.status() == OrderStatus.CANCELLED) {
            orderStockDeliveryService.cancelDraftForOrder(message.orderId());
        }
    }
}
