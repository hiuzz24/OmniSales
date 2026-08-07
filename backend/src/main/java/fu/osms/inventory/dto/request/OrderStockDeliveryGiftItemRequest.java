package fu.osms.inventory.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record OrderStockDeliveryGiftItemRequest(
        @NotNull(message = "Sản phẩm quà tặng là bắt buộc")
        UUID productVariantId,

        @NotNull(message = "Số lượng quà tặng là bắt buộc")
        @Positive(message = "Số lượng quà tặng phải lớn hơn 0")
        Integer quantity
) {
}
