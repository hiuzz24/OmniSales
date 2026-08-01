package fu.osms.inventory.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record OrderStockDeliveryCreateRequest(
        @NotNull(message = "Đơn hàng là bắt buộc")
        UUID orderId,

        List<@Valid OrderStockDeliveryGiftItemRequest> giftItems
) {
    public OrderStockDeliveryCreateRequest {
        giftItems = giftItems == null ? List.of() : List.copyOf(giftItems);
    }
}
