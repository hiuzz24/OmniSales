package fu.osms.order.service;

import fu.osms.common.dto.PageResponse;
import fu.osms.order.dto.request.OrderRequest;
import fu.osms.order.dto.response.OrderResponse;
import fu.osms.order.enums.OrderStatus;

import java.util.UUID;

public interface OrderService {

    OrderResponse create(OrderRequest request);

    OrderResponse getById(UUID id);

    PageResponse<OrderResponse> getAll(int page, int size);

    PageResponse<OrderResponse> getByStatus(OrderStatus status, int page, int size);

    OrderResponse updateStatus(UUID id, OrderStatus status);

    OrderResponse update(UUID id, OrderRequest request);

    void cancel(UUID id, String reason);
}
