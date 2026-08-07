package fu.osms.orderreturn.service;

import fu.osms.channel.entity.Channel;
import fu.osms.orderreturn.model.OrderReturnSnapshot;

import java.util.Optional;
import java.util.UUID;

public interface OrderReturnPersistenceService {
    Optional<UUID> upsert(Channel channel, OrderReturnSnapshot snapshot);
}
