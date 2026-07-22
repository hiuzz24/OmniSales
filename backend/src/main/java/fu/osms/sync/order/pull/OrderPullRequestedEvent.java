package fu.osms.sync.order.pull;

import java.util.UUID;
import java.time.OffsetDateTime;

public record OrderPullRequestedEvent(UUID syncLogId, OffsetDateTime from, OffsetDateTime to) {
}
