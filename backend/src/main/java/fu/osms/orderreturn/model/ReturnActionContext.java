package fu.osms.orderreturn.model;

import fu.osms.common.enums.PlatformType;
import fu.osms.orderreturn.enums.ReturnAction;

import java.util.List;
import java.util.UUID;

public record ReturnActionContext(
        UUID returnId,
        UUID channelId,
        PlatformType platform,
        String externalReturnId,
        ReturnAction action,
        UUID requestId,
        List<Item> items
) {
    public record Item(
            String externalReturnItemId,
            int approvedQuantity,
            Integer receivedQuantity,
            Integer restockableQuantity,
            Integer damagedQuantity,
            Integer missingQuantity
    ) {
    }
}
