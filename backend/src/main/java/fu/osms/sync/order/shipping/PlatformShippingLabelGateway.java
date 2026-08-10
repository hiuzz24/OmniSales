package fu.osms.sync.order.shipping;

import fu.osms.channel.entity.Channel;
import fu.osms.common.enums.PlatformType;
import fu.osms.order.dto.response.OrderShippingLabelResponse;
import fu.osms.order.enums.OrderStatus;

import java.util.Map;
import java.util.UUID;

public interface PlatformShippingLabelGateway {

    PlatformType getPlatform();

    OrderShippingLabelResponse createLabel(OrderShippingLabelContext context);

    record OrderShippingLabelContext(
            UUID orderId,
            String externalOrderId,
            PlatformType platform,
            OrderStatus status,
            Channel channel,
            Map<String, Object> platformMetadata
    ) {
    }
}
