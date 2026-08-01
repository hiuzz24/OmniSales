package fu.osms.orderreturn.service;

import fu.osms.orderreturn.enums.ReturnAction;
import fu.osms.orderreturn.model.ReturnRejectCommand;
import fu.osms.orderreturn.model.ReturnRejectOptions;

import java.util.UUID;

public interface OrderReturnActionService {
    void execute(UUID returnId, ReturnAction action, ReturnRejectCommand rejectCommand);

    ReturnRejectOptions getRejectOptions(UUID returnId);

    void validateInspection(UUID returnId);

    void refresh(UUID returnId);

    void check(UUID returnId);

    void retry(UUID returnId);
}
