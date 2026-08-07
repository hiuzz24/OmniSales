package fu.osms.inventory.service;

import fu.osms.inventory.dto.request.OrderStockDeliveryBatchRequest;
import fu.osms.inventory.dto.request.OrderStockDeliveryGiftItemRequest;
import fu.osms.inventory.dto.response.OrderStockDeliveryBatchResponse;
import fu.osms.inventory.dto.response.OrderStockDeliveryCandidateResponse;
import fu.osms.inventory.dto.response.StockDeliveryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;
import java.util.List;

public interface OrderStockDeliveryService {

    Page<OrderStockDeliveryCandidateResponse> getCandidates(UUID orderId, String keyword, Pageable pageable);

    OrderStockDeliveryBatchResponse createFromOrders(OrderStockDeliveryBatchRequest request);

    StockDeliveryResponse createFromOrder(UUID orderId, List<OrderStockDeliveryGiftItemRequest> giftItems);

    void completeForOrder(UUID orderId);

    void cancelDraftForOrder(UUID orderId);
}
