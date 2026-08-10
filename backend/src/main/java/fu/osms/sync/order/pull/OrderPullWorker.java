package fu.osms.sync.order.pull;

import java.util.UUID;

public interface OrderPullWorker {
    void process(UUID orderPullJobId);
}
