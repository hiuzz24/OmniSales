package fu.osms.inventory.service.impl;

import fu.osms.inventory.dto.request.OrderStockDeliveryBatchRequest;
import fu.osms.inventory.dto.response.OrderStockDeliveryBatchItemResponse;
import fu.osms.inventory.dto.response.OrderStockDeliveryBatchResponse;
import fu.osms.inventory.dto.response.StockDeliveryResponse;
import fu.osms.inventory.service.OrderStockDeliveryService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@Service
public class OrderStockDeliveryBatchService {

    private final ObjectProvider<OrderStockDeliveryService> orderStockDeliveryServiceProvider;

    public OrderStockDeliveryBatchService(
            ObjectProvider<OrderStockDeliveryService> orderStockDeliveryServiceProvider) {
        this.orderStockDeliveryServiceProvider = orderStockDeliveryServiceProvider;
    }

    public OrderStockDeliveryBatchResponse create(OrderStockDeliveryBatchRequest request) {
        List<OrderStockDeliveryBatchItemResponse> results = new ArrayList<>();
        OrderStockDeliveryService orderStockDeliveryService =
                orderStockDeliveryServiceProvider.getObject();
        for (UUID orderId : new LinkedHashSet<>(request.orderIds())) {
            try {
                StockDeliveryResponse response = orderStockDeliveryService.createFromOrder(orderId);
                results.add(OrderStockDeliveryBatchItemResponse.created(orderId, response));
            } catch (Exception exception) {
                results.add(OrderStockDeliveryBatchItemResponse.failed(orderId, readableMessage(exception)));
            }
        }
        int successCount = (int) results.stream().filter(item -> "CREATED".equals(item.status())).count();
        return new OrderStockDeliveryBatchResponse(successCount, results.size() - successCount, results);
    }

    private String readableMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "Không thể tạo phiếu xuất cho đơn hàng" : message;
    }
}
