package fu.osms.orderreturn.listener;

import fu.osms.orderreturn.enums.ReturnAction;
import fu.osms.orderreturn.event.OrderReturnInspectedEvent;
import fu.osms.orderreturn.service.OrderReturnActionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class OrderReturnInspectionListener {

    private final OrderReturnActionService actionService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInspected(OrderReturnInspectedEvent event) {
        actionService.execute(event.returnId(), ReturnAction.PROCESS, null);
    }
}
