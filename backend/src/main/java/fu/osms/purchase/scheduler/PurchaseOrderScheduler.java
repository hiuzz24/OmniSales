package fu.osms.purchase.scheduler;

import fu.osms.purchase.service.PurchaseOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseOrderScheduler {
    private final PurchaseOrderService purchaseOrderService;
    private final AtomicBoolean transitionFailureLogged = new AtomicBoolean(false);

    @Scheduled(fixedDelayString = "${app.purchase-order-transition-delay-ms:1000}")
    public void transitionSentOrders() {
        try {
            int count = purchaseOrderService.moveSentOrdersToReceiving();
            if (transitionFailureLogged.getAndSet(false)) {
                log.info("[PurchaseOrder] Automatic status transition recovered");
            }
            if (count > 0) {
                log.info("[PurchaseOrder] Moved {} sent order(s) to RECEIVING", count);
            }
        } catch (RuntimeException exception) {
            if (transitionFailureLogged.compareAndSet(false, true)) {
                log.error("[PurchaseOrder] Automatic status transition failed; scheduler will keep retrying",
                        exception);
            } else {
                log.debug("[PurchaseOrder] Automatic status transition is still failing: {}",
                        exception.getMessage());
            }
        }
    }
}
