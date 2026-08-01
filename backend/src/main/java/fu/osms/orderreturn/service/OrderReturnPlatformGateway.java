package fu.osms.orderreturn.service;

import fu.osms.common.enums.PlatformType;
import fu.osms.orderreturn.model.ReturnActionContext;
import fu.osms.orderreturn.model.ReturnPlatformActionResult;
import fu.osms.orderreturn.model.ReturnRejectCommand;
import fu.osms.orderreturn.model.ReturnRejectOptions;

public interface OrderReturnPlatformGateway {
    PlatformType platform();

    ReturnPlatformActionResult approve(ReturnActionContext context);

    ReturnPlatformActionResult reject(ReturnActionContext context, ReturnRejectCommand command);

    ReturnPlatformActionResult process(ReturnActionContext context);

    ReturnPlatformActionResult check(ReturnActionContext context);

    default void validateInspection(ReturnActionContext context) {
    }

    default ReturnPlatformActionResult refresh(ReturnActionContext context) {
        throw new UnsupportedOperationException("Refreshing return data is not supported for " + platform());
    }

    default ReturnRejectOptions rejectOptions(ReturnActionContext context) {
        return new ReturnRejectOptions(false, true, java.util.List.of(), null);
    }
}
