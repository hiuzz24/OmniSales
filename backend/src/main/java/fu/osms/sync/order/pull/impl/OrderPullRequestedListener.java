package fu.osms.sync.order.pull.impl;

import fu.osms.sync.order.pull.OrderPullRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class OrderPullRequestedListener {
    private final OrderPullDispatchService dispatchService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRequested(OrderPullRequestedEvent event) {
        dispatchService.dispatch(event.orderPullJobId());
    }
}
