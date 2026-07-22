package fu.osms.sync.order.pull;

import fu.osms.channel.entity.Channel;

import java.util.UUID;

public record OrderPullJobContext(UUID syncLogId, Channel channel) {
}
