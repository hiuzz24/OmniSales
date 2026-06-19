package fu.osms.inventory.controller;

import fu.osms.common.dto.ApiResponse;
import fu.osms.inventory.dto.request.StockDeliveryRequest;
import fu.osms.inventory.dto.response.StockDeliveryResponse;
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
import java.util.UUID;

@RestController
@RequestMapping("/api/stock-deliveries")
@RequiredArgsConstructor
public class StockDeliveryController {

    private final StockDeliveryService stockDeliveryService;

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
}
