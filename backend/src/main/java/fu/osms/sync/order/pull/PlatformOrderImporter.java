package fu.osms.sync.order.pull;

import fu.osms.channel.entity.Channel;
import fu.osms.common.enums.PlatformType;

import java.time.OffsetDateTime;

public interface PlatformOrderImporter {
    PlatformType platform();
    OrderPullBatchResult pull(Channel channel, OffsetDateTime from, OffsetDateTime to);
}
