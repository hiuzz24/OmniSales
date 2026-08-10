package fu.osms.sync.order.pull;

import fu.osms.channel.entity.Channel;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OrderPullJobContext(
        UUID jobId,
        UUID syncLogId,
        Channel channel,
        OffsetDateTime from,
        OffsetDateTime to,
        int attemptCount
) {
}
