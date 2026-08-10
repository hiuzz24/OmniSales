package fu.osms.messaging.listener;

import fu.osms.messaging.constants.RabbitMQConstants;
import fu.osms.messaging.dto.OrderPullMessage;
import fu.osms.sync.order.pull.OrderPullWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPullEventListener {

    private final OrderPullWorker worker;

    @RabbitListener(queues = RabbitMQConstants.QUEUE_ORDER_PULL, concurrency = "1-2")
    public void onOrderPull(OrderPullMessage message) {
        log.info("[OrderPullEventListener] Processing order pull jobId={}", message.orderPullJobId());
        worker.process(message.orderPullJobId());
    }
}

