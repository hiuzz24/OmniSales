package fu.osms.purchase.dto;

import fu.osms.purchase.enums.PurchaseOrderStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class PurchaseOrderResponse {
    private UUID id;
    private String orderCode;
    private UUID supplierId;
    private String supplierName;
    private String supplierTaxCode;
    private UUID warehouseId;
    private String warehouseName;
    private String warehouseAddress;
    private PurchaseOrderStatus status;
    private OffsetDateTime orderDate;
    private LocalDate expectedReceiptDate;
    private String paymentMethod;
    private BigDecimal totalAmount;
    private String notes;
    private UUID createdById;
    private String createdByName;
    private UUID receiptId;
    private String receiptCode;
    private int receiptCount;
    private List<ReceiptSummary> receipts;
    private String evidenceUrl;
    private OffsetDateTime sentAt;
    private OffsetDateTime receivingAt;
    private OffsetDateTime completedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private List<PurchaseOrderItemResponse> items;

    @Data
    @Builder
    public static class ReceiptSummary {
        private UUID id;
        private String receiptCode;
    }
}
