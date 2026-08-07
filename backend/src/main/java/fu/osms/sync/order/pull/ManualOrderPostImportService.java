package fu.osms.sync.order.pull;

import fu.osms.sync.order.importing.OrderImportOutcome;

public interface ManualOrderPostImportService {
    void publish(OrderImportOutcome outcome);
}
