package fu.osms.purchase.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class InspectionItemRequest {
    @NotNull(message = "Variant ID là bắt buộc")
    private UUID variantId;

    @NotNull(message = "Số lượng thực nhận là bắt buộc")
    @Min(value = 0, message = "Số lượng thực nhận phải >= 0")
    private Integer actualQuantity;

    private String surplusNote;
}
