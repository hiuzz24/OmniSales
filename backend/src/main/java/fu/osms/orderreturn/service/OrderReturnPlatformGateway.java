package fu.osms.orderreturn.service;

import fu.osms.common.enums.PlatformType;
import fu.osms.orderreturn.model.ReturnActionContext;
import fu.osms.orderreturn.model.ReturnPlatformActionResult;

public interface OrderReturnPlatformGateway {
    PlatformType platform();

    ReturnPlatformActionResult approve(ReturnActionContext context);

    ReturnPlatformActionResult reject(ReturnActionContext context, String reason);

    ReturnPlatformActionResult process(ReturnActionContext context);

    ReturnPlatformActionResult check(ReturnActionContext context);
}
