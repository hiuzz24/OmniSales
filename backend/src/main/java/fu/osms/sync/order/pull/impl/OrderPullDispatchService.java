package fu.osms.sync.order.pull.impl;

import fu.osms.messaging.constants.RabbitMQConstants;
import fu.osms.messaging.dto.OrderPullMessage;
import fu.osms.messaging.publisher.EventPublisher;
import fu.osms.sync.order.pull.OrderPullJobStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderPullDispatchService {

    private final OrderPullJobStore jobStore;
    private final EventPublisher eventPublisher;
    private final OrderPullLocalFallback localFallback;

    public void dispatch(UUID jobId) {
        if (!jobStore.prepareDispatch(jobId)) {
            return;
        }
        publishPrepared(jobId);
    }

    public void publishPrepared(UUID jobId) {
        log.info("[OrderPullDispatch] Publishing order pull jobId={}", jobId);
        eventPublisher.publish(
                RabbitMQConstants.ORDER_PULL_REQUESTED,
                new OrderPullMessage(jobId),
                () -> localFallback.processAsync(jobId));
    }
}

