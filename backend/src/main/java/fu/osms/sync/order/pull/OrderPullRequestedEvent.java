package fu.osms.sync.order.pull;

import java.util.UUID;

public record OrderPullRequestedEvent(UUID orderPullJobId) {
}
