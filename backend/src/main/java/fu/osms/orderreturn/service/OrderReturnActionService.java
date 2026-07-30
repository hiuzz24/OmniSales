package fu.osms.orderreturn.service;

import fu.osms.orderreturn.enums.ReturnAction;

import java.util.UUID;

public interface OrderReturnActionService {
    void execute(UUID returnId, ReturnAction action, String reason);

    void check(UUID returnId);

    void retry(UUID returnId);
}
