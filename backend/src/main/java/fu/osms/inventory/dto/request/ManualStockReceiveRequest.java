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

/**
 * Request DTO for creating a manual stock receipt (nhập thủ công).
 * Unlike {@link StockReceiveRequest}, this does NOT require a purchase order.
 * Warehouse must be explicitly supplied by the caller.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ManualStockReceiveRequest {

    @NotNull(message = "Kho nhập là bắt buộc")
    private UUID warehouseId;

    /** Supplier is optional for manual receipts. */
    private UUID supplierId;

    @Size(max = 100)
    private String invoiceNumber;

    @NotNull(message = "Ngày nhập là bắt buộc")
    @PastOrPresent(message = "Ngày nhập không được lớn hơn ngày hiện tại")
    private LocalDate receivedAt;

    private String notes;

    @NotEmpty(message = "Phiếu nhập phải có ít nhất một sản phẩm")
    @Valid
    private List<StockReceiveItemRequest> items;

    /** True → save as DRAFT; false (default) → confirm immediately. */
    @Builder.Default
    private Boolean isDraft = false;
}
