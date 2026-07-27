package fu.osms.inventory.service;

import fu.osms.inventory.dto.response.OrderStockDeliveryReadinessResponse;

import java.util.UUID;

public interface OrderStockDeliveryReadinessService {

    OrderStockDeliveryReadinessResponse getReadiness(UUID orderId);

    void requireReadyForShipment(UUID orderId);
}
