package fu.osms.inventory.dto.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record OrderStockDeliveryBatchRequest(
        @NotEmpty(message = "Vui lòng chọn ít nhất một đơn hàng")
        List<UUID> orderIds
) {
}
