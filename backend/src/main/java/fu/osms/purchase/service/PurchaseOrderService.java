package fu.osms.purchase.service;

import fu.osms.common.dto.PageResponse;
import fu.osms.purchase.dto.AutoCreatedOrderResult;
import fu.osms.purchase.dto.InspectionItemRequest;
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
    PurchaseOrderResponse confirmReceiving(UUID id);
    void completeFromReceipt(UUID purchaseOrderId);
    /** Complete the purchase order from a receipt, auto-creating shortage/surplus orders if needed.
     *  Returns the auto-created order info if one was created, empty otherwise. */
    Optional<AutoCreatedOrderResult> completeFromReceiptWithResult(UUID purchaseOrderId);
    /** Save actual quantities (stays in INSPECTING — "Tiếp tục" / draft save). */
    PurchaseOrderResponse saveInspection(UUID id, List<InspectionItemRequest> items);
    /** Finalize inspection → INSPECTED ("Hoàn thành kiểm tra"). */
    PurchaseOrderResponse completeInspection(UUID id, List<InspectionItemRequest> items);
    /** If any items have surplus (actualQty > orderedQty), spin off a new INSPECTED order for the surplus quantities. */
    PurchaseOrderResponse createSurplusOrder(UUID originalOrderId);
    /** If any items have shortage (actualQty < orderedQty), create a supplementary INSPECTED order for the missing quantities. */
    PurchaseOrderResponse createShortageOrder(UUID originalOrderId);
}
