package fu.osms.order.service.impl;

import fu.osms.order.dto.response.OrderBatchCancelItemResponse;
import fu.osms.order.service.OrderBatchCancelService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderBatchCancelServiceImpl implements OrderBatchCancelService {
    private final OrderBatchCancelExecutorServiceImpl executor;

    @Override
    public List<OrderBatchCancelItemResponse> cancelWaitingStock(List<UUID> orderIds) {
        List<OrderBatchCancelItemResponse> result = new ArrayList<>();
        for (UUID orderId : new LinkedHashSet<>(orderIds == null ? List.of() : orderIds)) {
            try {
                result.add(executor.cancelOne(orderId));
            } catch (Exception exception) {
                String message = exception.getMessage() == null
                        ? "Không thể hủy đơn" : exception.getMessage();
                result.add(new OrderBatchCancelItemResponse(orderId, false, message));
            }
        }
        return result;
    }
}
