package fu.osms.inventory.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockReceiveRequest {

    @NotNull(message = "Shop ID must not be null")

    @NotNull(message = "Warehouse ID must not be null")
    private UUID warehouseId;

    private UUID supplierId;

    @Size(max = 100)
    private String receiptCode;

    @NotNull(message = "Ngày nhập là bắt buộc")
    @PastOrPresent(message = "Ngày nhập không được lớn hơn ngày hiện tại")
    private LocalDate receivedAt;

    @Size(max = 100)
    private String invoiceNumber;

    private String notes;

    @NotEmpty(message = "Receipt must have at least one item")
    @Valid
    private List<StockReceiveItemRequest> items;

    // Flag to indicate if this receipt should be saved as DRAFT
    @Builder.Default
    private Boolean isDraft = false;
}
