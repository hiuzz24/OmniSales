package fu.osms.inventory.controller;

import fu.osms.common.dto.ApiResponse;
import fu.osms.inventory.dto.request.StockDeliveryFromReceiptRequest;
import fu.osms.inventory.dto.request.StockDeliveryRequest;
import fu.osms.inventory.dto.request.OrderStockDeliveryBatchRequest;
import fu.osms.inventory.dto.response.OrderStockDeliveryBatchResponse;
import fu.osms.inventory.dto.response.OrderStockDeliveryCandidateResponse;
import fu.osms.inventory.dto.response.OrderStockDeliveryReadinessResponse;
import fu.osms.inventory.dto.response.StockDeliveryResponse;
import fu.osms.inventory.service.OrderStockDeliveryReadinessService;
import fu.osms.inventory.service.OrderStockDeliveryService;
import fu.osms.inventory.service.StockDeliveryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/stock-deliveries")
@RequiredArgsConstructor
public class StockDeliveryController {

    private final StockDeliveryService stockDeliveryService;
    private final OrderStockDeliveryService orderStockDeliveryService;
    private final OrderStockDeliveryReadinessService orderStockDeliveryReadinessService;

    @GetMapping("/order-candidates")
    @PreAuthorize("hasAnyRole('OWNER', 'OPERATIONS')")
    public ResponseEntity<ApiResponse<Page<OrderStockDeliveryCandidateResponse>>> getOrderCandidates(
            @RequestParam(required = false) UUID orderId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = orderId == null
                ? PageRequest.of(page, size, Sort.by("createdAt").descending())
                : PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.success(
                "Order candidates retrieved successfully",
                orderStockDeliveryService.getCandidates(orderId, keyword, pageable)));
    }

    @GetMapping("/orders/{orderId}/readiness")
    @PreAuthorize("hasAnyRole('OWNER', 'OPERATIONS', 'SALES')")
    public ResponseEntity<ApiResponse<OrderStockDeliveryReadinessResponse>> getOrderReadiness(
            @PathVariable UUID orderId) {
        return ResponseEntity.ok(ApiResponse.success(
                orderStockDeliveryReadinessService.getReadiness(orderId)));
    }

    @PostMapping("/from-orders")
    @PreAuthorize("hasAnyRole('OWNER', 'OPERATIONS')")
    public ResponseEntity<ApiResponse<OrderStockDeliveryBatchResponse>> createFromOrders(
            @Valid @RequestBody OrderStockDeliveryBatchRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Order stock deliveries processed",
                orderStockDeliveryService.createFromOrders(request)));
    }

    /**
     * Create a new stock delivery document
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER', 'OPERATIONS')")
    public ResponseEntity<ApiResponse<StockDeliveryResponse>> createStockDelivery(
            @Valid @RequestBody StockDeliveryRequest request) {

        StockDeliveryResponse response = stockDeliveryService.createStockDelivery(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Stock delivery created successfully", response));
    }

    /**
     * Create a stock delivery document based on a confirmed stock receipt
     * (return goods to supplier flow)
     */
    @PostMapping("/from-receipt/{receiptId}")
    @PreAuthorize("hasAnyRole('OWNER', 'OPERATIONS')")
    public ResponseEntity<ApiResponse<StockDeliveryResponse>> createStockDeliveryFromReceipt(
            @PathVariable UUID receiptId,
            @RequestBody(required = false) @Valid StockDeliveryFromReceiptRequest request) {

        StockDeliveryResponse response = stockDeliveryService.createStockDeliveryFromReceipt(receiptId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Stock delivery created from receipt successfully", response));
    }

    /**
     * Update a draft stock delivery document
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'OPERATIONS')")
    public ResponseEntity<ApiResponse<StockDeliveryResponse>> updateStockDelivery(
            @PathVariable UUID id,
            @Valid @RequestBody StockDeliveryRequest request) {

        StockDeliveryResponse response = stockDeliveryService.updateStockDelivery(id, request);
        return ResponseEntity.ok(ApiResponse.success("Stock delivery updated successfully", response));
    }

    /**
     * Get stock delivery by ID
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'OPERATIONS')")
    public ResponseEntity<ApiResponse<StockDeliveryResponse>> getStockDeliveryById(
            @PathVariable UUID id) {

        StockDeliveryResponse response = stockDeliveryService.getStockDeliveryById(id);
        return ResponseEntity.ok(ApiResponse.success("Stock delivery retrieved successfully", response));
    }

    /**
     * Get all stock deliveries with filters and pagination
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'OPERATIONS')")
    public ResponseEntity<ApiResponse<Page<StockDeliveryResponse>>> getAllStockDeliveries(
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String deliveryType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {

        Sort sort = sortDirection.equalsIgnoreCase("ASC") 
                ? Sort.by(sortBy).ascending() 
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<StockDeliveryResponse> response = stockDeliveryService.getAllStockDeliveries(
                warehouseId, status, deliveryType, keyword, startDate, endDate, pageable);

        return ResponseEntity.ok(ApiResponse.success("Stock deliveries retrieved successfully", response));
    }

    /**
     * Confirm stock delivery
     */
    @PutMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('OWNER', 'OPERATIONS')")
    public ResponseEntity<ApiResponse<StockDeliveryResponse>> confirmStockDelivery(
            @PathVariable UUID id) {

        StockDeliveryResponse response = stockDeliveryService.confirmStockDelivery(id);
        return ResponseEntity.ok(ApiResponse.success("Stock delivery confirmed successfully", response));
    }

    /**
     * Cancel stock delivery
     */
    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<StockDeliveryResponse>> cancelStockDelivery(
            @PathVariable UUID id) {

        StockDeliveryResponse response = stockDeliveryService.cancelStockDelivery(id);
        return ResponseEntity.ok(ApiResponse.success("Stock delivery cancelled successfully", response));
    }

    /**
     * Get delivery statistics
     */
    @GetMapping("/statistics")
    @PreAuthorize("hasAnyRole('OWNER', 'OPERATIONS')")
    public ResponseEntity<ApiResponse<Object>> getDeliveryStatistics() {
        Object statistics = stockDeliveryService.getDeliveryStatistics();
        return ResponseEntity.ok(ApiResponse.success("Statistics retrieved successfully", statistics));
    }

    @PostMapping("/sync-marketplace-inventory")
    @PreAuthorize("hasAnyRole('OWNER', 'OPERATIONS')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> syncPendingMarketplaceInventory() {
        int syncedVariantCount = stockDeliveryService.syncPendingMarketplaceInventory();
        return ResponseEntity.ok(ApiResponse.success(
                "Đồng bộ tồn kho phiếu xuất lên các sàn thành công",
                Map.of("syncedVariantCount", syncedVariantCount)
        ));
    }
}
