package fu.osms.purchase.service;

import fu.osms.common.dto.PageResponse;
import fu.osms.purchase.dto.AutoCreatedOrderResult;
import fu.osms.purchase.dto.PurchaseOrderRequest;
import fu.osms.purchase.dto.PurchaseOrderResponse;
import fu.osms.purchase.dto.PurchaseOrderFormOptionsResponse;
import fu.osms.purchase.enums.PurchaseOrderStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseOrderService {
    PurchaseOrderResponse create(PurchaseOrderRequest request, UUID userId);
    PurchaseOrderResponse updateDraft(UUID id, PurchaseOrderRequest request);
    PurchaseOrderResponse sendToSupplier(UUID id);
    PurchaseOrderResponse cancel(UUID id);
    PurchaseOrderResponse getById(UUID id);
    PageResponse<PurchaseOrderResponse> getAll(int page, int size, PurchaseOrderStatus status);
    Map<String, Long> getStatistics();
    PurchaseOrderFormOptionsResponse getFormOptions();
    String generateOrderCode();
    /** Supplier confirmed the shipment is in transit → SENT_TO_SUPPLIER → RECEIVING (Đang vận chuyển). */
    PurchaseOrderResponse confirmShipping(UUID id);
    void completeFromReceipt(UUID purchaseOrderId);
    /** Evaluate whether a purchase order is now fully received after a receipt.
     *  Transitions the order to COMPLETED only when every ordered variant has
     *  been received in full across all confirmed receipts. */
    Optional<AutoCreatedOrderResult> completeFromReceiptWithResult(UUID purchaseOrderId);
    /** Replace the purchase order evidence image (uploading a new one replaces the old). */
    PurchaseOrderResponse updateEvidence(UUID id, String evidenceUrl);
}
