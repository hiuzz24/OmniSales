package fu.osms.sync.order.pull.impl;

import fu.osms.sync.order.pull.OrderPullWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OrderPullLocalFallback {

    private final OrderPullWorker worker;

    @Async("orderPullExecutor")
    public void processAsync(UUID jobId) {
        worker.process(jobId);
    }
}

