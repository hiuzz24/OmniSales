package fu.osms.sync.order.pull.impl;

import fu.osms.sync.order.pull.OrderPullRequestedEvent;
import fu.osms.sync.order.pull.OrderPullWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.RejectedExecutionException;

@Component
@RequiredArgsConstructor
public class OrderPullRequestedListener {
    private final OrderPullWorker worker;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRequested(OrderPullRequestedEvent event) {
        try {
            worker.processAsync(event.syncLogId(), event.from(), event.to());
        } catch (RejectedExecutionException e) {
            worker.markQueueRejected(event.syncLogId());
        }
    }
}
