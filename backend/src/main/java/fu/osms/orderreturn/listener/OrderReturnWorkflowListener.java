package fu.osms.orderreturn.listener;

import fu.osms.orderreturn.event.OrderReturnChangedEvent;
import fu.osms.orderreturn.service.OrderReturnInventoryPostingService;
import fu.osms.orderreturn.service.OrderReturnPaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderReturnWorkflowListener {

    private final OrderReturnPaymentService paymentService;
    private final OrderReturnInventoryPostingService inventoryPostingService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChanged(OrderReturnChangedEvent event) {
        paymentService.projectPayment(event.returnId());
        try {
            inventoryPostingService.postIfReady(event.returnId());
        } catch (Exception exception) {
            log.error("[OrderReturn] Failed to post return stock returnId={}", event.returnId(), exception);
            inventoryPostingService.markPending(event.returnId(), rootMessage(exception));
        }
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
