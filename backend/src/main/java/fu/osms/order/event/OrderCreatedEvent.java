package fu.osms.order.event;

import fu.osms.order.entity.Order;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class OrderCreatedEvent {
    private final Order order;
}
