package fu.osms.sync.order.pull;

import java.util.List;

public record OrderPullBatchResult(int total, int success, int failed, List<String> errors) {
}
