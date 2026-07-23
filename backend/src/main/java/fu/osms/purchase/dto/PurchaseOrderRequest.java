package fu.osms.purchase.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
public class PurchaseOrderRequest {
    @Size(max = 100)
    @Pattern(regexp = "^MĐH-\\d{4}-\\d{6}$", message = "Mã đơn phải có định dạng MĐH-năm-6 chữ số")
    private String orderCode;

    @NotNull
    private UUID supplierId;

    @NotNull
    @FutureOrPresent
    private LocalDate expectedReceiptDate;

    @Size(max = 50)
    private String paymentMethod;

    private String notes;

    private Boolean isDraft = false;

    @Valid
    @NotEmpty
    private List<PurchaseOrderItemRequest> items;
}
