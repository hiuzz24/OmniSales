package fu.osms.sync.order.importing;

import fu.osms.channel.entity.Channel;
import fu.osms.common.enums.PlatformType;

public interface OrderUpsertSupport {
    OrderUpsertResult ensureAndLock(Channel channel, String externalOrderId, PlatformType platform);
}
