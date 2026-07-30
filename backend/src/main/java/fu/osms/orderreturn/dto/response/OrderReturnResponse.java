package fu.osms.orderreturn.dto.response;

import fu.osms.common.enums.PlatformType;
import fu.osms.orderreturn.enums.OrderReturnStatus;
import fu.osms.orderreturn.enums.ReturnAction;
import fu.osms.orderreturn.enums.ReturnActionState;
import fu.osms.orderreturn.enums.ReturnDataValidationState;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record OrderReturnResponse(
        UUID id,
        UUID orderId,
        String externalOrderId,
        UUID channelId,
        String channelName,
        PlatformType platform,
        UUID warehouseId,
        String warehouseName,
        String externalReturnId,
        String platformStatus,
        OrderReturnStatus status,
        ReturnDataValidationState dataValidationState,
        ReturnAction lastAction,
        ReturnActionState actionState,
        String actionError,
        String lastSyncError,
        OffsetDateTime platformUpdatedAt,
        OffsetDateTime approvedAt,
        OffsetDateTime inspectedAt,
        OffsetDateTime refundConfirmedAt,
        OffsetDateTime inventoryPostedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<OrderReturnItemResponse> items
) {
}
