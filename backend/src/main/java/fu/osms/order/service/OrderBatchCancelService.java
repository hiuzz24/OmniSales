package fu.osms.order.service;

import fu.osms.order.dto.response.OrderBatchCancelItemResponse;

import java.util.List;
import java.util.UUID;

public interface OrderBatchCancelService {
    List<OrderBatchCancelItemResponse> cancelWaitingStock(List<UUID> orderIds);
}
