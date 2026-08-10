package fu.osms.purchase.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Automatic SENT_TO_SUPPLIER → RECEIVING transition has been removed.
 * Users with SALES or OWNER role must manually confirm receiving via
 * PATCH /api/purchase-orders/{id}/confirm-receiving.
 */
@Slf4j
@Component
public class PurchaseOrderScheduler {
    // Scheduler disabled — transition is now manual (confirmShipping endpoint).
}
