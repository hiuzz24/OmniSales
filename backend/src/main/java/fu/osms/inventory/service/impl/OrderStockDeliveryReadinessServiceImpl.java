package fu.osms.inventory.service.impl;

import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.inventory.dto.response.OrderStockDeliveryReadinessResponse;
import fu.osms.inventory.entity.InventoryIssue;
import fu.osms.inventory.repository.InventoryIssueRepository;
import fu.osms.inventory.service.OrderStockDeliveryReadinessService;
import fu.osms.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderStockDeliveryReadinessServiceImpl implements OrderStockDeliveryReadinessService {

    private static final String ORDER_ISSUE = "ORDER";
    private static final String DRAFT = "DRAFT";

    private final OrderRepository orderRepository;
    private final InventoryIssueRepository inventoryIssueRepository;

    @Override
    @Transactional(readOnly = true)
    public OrderStockDeliveryReadinessResponse getReadiness(UUID orderId) {
        if (!orderRepository.existsById(orderId)) {
            throw new AppException(ErrorCode.ORDER_NOT_FOUND);
        }

        InventoryIssue issue = inventoryIssueRepository
                .findFirstByReferenceIdAndIssueTypeAndStatus(orderId, ORDER_ISSUE, DRAFT)
                .orElse(null);
        return new OrderStockDeliveryReadinessResponse(
                orderId,
                issue != null,
                issue != null ? issue.getId() : null,
                issue != null ? issue.getIssueCode() : null,
                issue != null ? issue.getStatus() : null
        );
    }

    @Override
    @Transactional(readOnly = true)
    public void requireReadyForShipment(UUID orderId) {
        if (!getReadiness(orderId).readyForShipment()) {
            throw new AppException(ErrorCode.ORDER_STOCK_DELIVERY_REQUIRED);
        }
    }
}
