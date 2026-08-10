package fu.osms.order.service;

import fu.osms.order.dto.response.OrderShippingLabelResponse;

import java.util.UUID;

public interface OrderShippingLabelService {
    OrderShippingLabelResponse createLabel(UUID orderId);
}
