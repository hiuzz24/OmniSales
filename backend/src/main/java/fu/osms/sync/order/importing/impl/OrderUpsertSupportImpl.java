package fu.osms.sync.order.importing.impl;

import fu.osms.channel.entity.Channel;
import fu.osms.common.enums.PlatformType;
import fu.osms.order.entity.Order;
import fu.osms.order.repository.OrderRepository;
import fu.osms.sync.order.importing.OrderUpsertResult;
import fu.osms.sync.order.importing.OrderUpsertSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OrderUpsertSupportImpl implements OrderUpsertSupport {

    private final OrderRepository orderRepository;

    @Override
    public OrderUpsertResult ensureAndLock(Channel channel, String externalOrderId, PlatformType platform) {
        if (channel == null || channel.getId() == null) {
            throw new IllegalArgumentException("Platform order is missing channel");
        }
        if (externalOrderId == null || externalOrderId.isBlank()) {
            throw new IllegalArgumentException("Platform order is missing external order id");
        }

        int inserted = orderRepository.insertPlatformOrderIfAbsent(
                UUID.randomUUID(), channel.getId(), platform.name(), channel.getDisplayName(), externalOrderId);
        Order order = orderRepository.findForUpdateByChannelIdAndExternalOrderId(channel.getId(), externalOrderId)
                .orElseThrow(() -> new IllegalStateException("Cannot create or load platform order " + externalOrderId));
        assertBelongsToChannel(order, channel, platform);
        return new OrderUpsertResult(order, inserted > 0);
    }

    private void assertBelongsToChannel(Order order, Channel channel, PlatformType platform) {
        if (order.getChannel() != null && !channel.getId().equals(order.getChannel().getId())) {
            throw new IllegalStateException("Resolved order belongs to another channel");
        }
        if (order.getPlatform() != null && order.getPlatform() != platform) {
            throw new IllegalStateException("Resolved order belongs to another platform");
        }
    }
}
