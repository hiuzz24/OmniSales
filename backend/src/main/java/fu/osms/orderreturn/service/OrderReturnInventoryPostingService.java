package fu.osms.orderreturn.service;

import java.util.UUID;

public interface OrderReturnInventoryPostingService {
    void postIfReady(UUID returnId);

    void markPending(UUID returnId, String error);
}
