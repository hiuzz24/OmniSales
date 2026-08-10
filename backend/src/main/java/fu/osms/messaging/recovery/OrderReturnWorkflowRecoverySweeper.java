package fu.osms.messaging.recovery;

import fu.osms.messaging.constants.RabbitMQConstants;
import fu.osms.messaging.dto.OrderReturnWorkflowMessage;
import fu.osms.messaging.handler.OrderReturnWorkflowHandler;
import fu.osms.messaging.publisher.EventPublisher;
import fu.osms.orderreturn.entity.OrderReturn;
import fu.osms.orderreturn.enums.OrderReturnStatus;
import fu.osms.orderreturn.repository.OrderReturnRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderReturnWorkflowRecoverySweeper {

    private static final long CACHE_MINUTES = 10;

    private final OrderReturnRepository returnRepository;
    private final EventPublisher eventPublisher;
    private final OrderReturnWorkflowHandler handler;
    private final Map<UUID, OffsetDateTime> recentlyPublished = new ConcurrentHashMap<>();

    @Scheduled(fixedDelayString = "${app.messaging.recovery.order-return-delay-ms:60000}")
    public void sweep() {
        OffsetDateTime now = OffsetDateTime.now();
        recentlyPublished.entrySet().removeIf(entry -> entry.getValue().isBefore(now.minusMinutes(CACHE_MINUTES)));
        List<OrderReturn> candidates = returnRepository
                .findByRefundConfirmedAtIsNotNullAndInventoryPostedAtIsNullAndStatusInOrderByUpdatedAtAsc(
                        List.of(OrderReturnStatus.INSPECTED, OrderReturnStatus.PLATFORM_PROCESSING),
                        PageRequest.of(0, 50));
        for (OrderReturn candidate : candidates) {
            if (recentlyPublished.putIfAbsent(candidate.getId(), now) != null) {
                continue;
            }
            OrderReturnWorkflowMessage message = new OrderReturnWorkflowMessage(
                    UUID.randomUUID(), candidate.getId());
            try {
                eventPublisher.publish(
                        RabbitMQConstants.ORDER_RETURN_WORKFLOW,
                        message,
                        () -> handler.handle(message));
            } catch (Exception exception) {
                recentlyPublished.remove(candidate.getId());
                log.warn("[OrderReturnRecovery] Publish failed returnId={}", candidate.getId(), exception);
            }
        }
    }
}
