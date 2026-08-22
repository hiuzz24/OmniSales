package fu.osms.order.dto.response;

import java.util.UUID;

public record OrderBatchCancelItemResponse(UUID orderId, boolean success, String message) {
}
