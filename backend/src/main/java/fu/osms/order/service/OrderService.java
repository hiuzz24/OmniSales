package fu.osms.order.service;

import fu.osms.common.dto.PageResponse;
import fu.osms.audit.dto.response.AuditLogResponse;
import fu.osms.order.dto.request.CancelOrderRequest;
import fu.osms.order.dto.response.CancelReasonResponse;
import fu.osms.order.dto.response.OrderResponse;
import fu.osms.order.dto.response.OrderStats;
import fu.osms.order.enums.OrderStatus;
import fu.osms.order.enums.PaymentStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface OrderService {

    OrderResponse getById(UUID id);

    PageResponse<OrderResponse> getAll(int page, int size);

    PageResponse<OrderResponse> getByStatus(OrderStatus status, int page, int size);

    PageResponse<OrderResponse> getFiltered(OrderStatus status, UUID channelId, String keyword,
                                            OffsetDateTime from, OffsetDateTime to,
                                            UUID customerId, Boolean waitingStockExpired, int page, int size);

    OrderResponse updateStatus(UUID id, OrderStatus status);

    OrderResponse confirmWaitingStock(UUID id);

    OrderResponse updatePaymentStatus(UUID id, PaymentStatus paymentStatus);

    void cancel(UUID id, CancelOrderRequest request);

    List<CancelReasonResponse> getCancelReasons(UUID id);

    OrderStats getStats();

    long countOrdersWithoutCustomer();

    PageResponse<AuditLogResponse> getOrderHistory(UUID orderId, int page, int size);
}
