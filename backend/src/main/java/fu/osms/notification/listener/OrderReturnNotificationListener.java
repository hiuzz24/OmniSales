package fu.osms.notification.listener;

import fu.osms.notification.service.OrderWorkflowNotificationService;
import fu.osms.orderreturn.entity.OrderReturn;
import fu.osms.orderreturn.event.OrderReturnProcessedEvent;
import fu.osms.orderreturn.repository.OrderReturnRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderReturnNotificationListener {

    private final OrderReturnRepository orderReturnRepository;
    private final OrderWorkflowNotificationService notificationService;

    @EventListener
    public void onProcessed(OrderReturnProcessedEvent event) {
        OrderReturn orderReturn = orderReturnRepository.findWithDetailsById(event.returnId()).orElse(null);
        if (orderReturn == null) return;

        String type;
        String title;
        String suffix;
        switch (orderReturn.getStatus()) {
            case PENDING_APPROVAL -> {
                type = "ORDER_RETURN_REQUESTED";
                title = "Có yêu cầu trả hàng mới";
                suffix = " đang chờ xử lý.";
            }
            case REJECTED -> {
                type = "ORDER_RETURN_REJECTED";
                title = "Yêu cầu trả hàng đã bị từ chối";
                suffix = " đã bị từ chối.";
            }
            case COMPLETED -> {
                type = "ORDER_RETURN_COMPLETED";
                title = "Hoàn tất trả hàng/hoàn tiền";
                suffix = " đã hoàn tất.";
            }
            case FAILED, PENDING_STOCK -> {
                type = "ORDER_RETURN_ATTENTION";
                title = "Yêu cầu trả hàng cần xử lý";
                suffix = " đang gặp lỗi hoặc chờ cập nhật tồn kho.";
            }
            default -> { return; }
        }

        String returnCode = orderReturn.getExternalReturnId() != null
                ? orderReturn.getExternalReturnId() : orderReturn.getId().toString();
        try {
            notificationService.notifyRolesOnce(
                    List.of("OWNER", "OPERATIONS", "SALES"), type, title,
                    "Yêu cầu trả hàng " + returnCode + suffix,
                    "RETURN", orderReturn.getId());
        } catch (Exception exception) {
            log.warn("Could not notify order return returnId={}: {}",
                    event.returnId(), exception.getMessage());
        }
    }
}
