package fu.osms.purchase.service;

import fu.osms.common.dto.PageResponse;
import fu.osms.purchase.dto.PurchaseOrderRequest;
import fu.osms.purchase.dto.PurchaseOrderResponse;
import fu.osms.purchase.dto.PurchaseOrderFormOptionsResponse;
import fu.osms.purchase.enums.PurchaseOrderStatus;

import java.util.Map;
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
    int moveSentOrdersToReceiving();
    void completeFromReceipt(UUID purchaseOrderId);
}
