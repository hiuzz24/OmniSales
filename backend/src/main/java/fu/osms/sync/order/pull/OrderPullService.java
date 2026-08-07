package fu.osms.sync.order.pull;

import fu.osms.sync.dto.SyncLogResponse;
import fu.osms.sync.order.pull.dto.OrderPullRequest;

import java.util.List;
import java.util.UUID;

public interface OrderPullService {
    List<SyncLogResponse> start(OrderPullRequest request);
    SyncLogResponse get(UUID syncLogId);
    List<SyncLogResponse> active();
}
