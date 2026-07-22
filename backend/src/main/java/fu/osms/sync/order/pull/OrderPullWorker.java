package fu.osms.sync.order.pull;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface OrderPullWorker {
    void processAsync(UUID syncLogId, OffsetDateTime from, OffsetDateTime to);
    void markQueueRejected(UUID syncLogId);
}
