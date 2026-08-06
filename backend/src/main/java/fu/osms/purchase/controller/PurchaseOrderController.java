package fu.osms.purchase.controller;

import fu.osms.auth.repository.UserRepository;
import fu.osms.common.dto.ApiResponse;
import fu.osms.common.dto.PageResponse;
import fu.osms.purchase.dto.InspectionItemRequest;
import fu.osms.purchase.dto.PurchaseOrderRequest;
import fu.osms.purchase.dto.PurchaseOrderResponse;
import fu.osms.purchase.dto.PurchaseOrderFormOptionsResponse;
import fu.osms.purchase.enums.PurchaseOrderStatus;
import fu.osms.purchase.service.PurchaseOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/purchase-orders")
@RequiredArgsConstructor
public class PurchaseOrderController {
    private final PurchaseOrderService service;
    private final UserRepository userRepository;

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER', 'SALES')")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> create(
            @Valid @RequestBody PurchaseOrderRequest request,
            @AuthenticationPrincipal UserDetails principal) {
        UUID userId = userRepository.findByEmail(principal.getUsername())
                .orElseThrow().getId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo đơn mua hàng thành công", service.create(request, userId)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'SALES')")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody PurchaseOrderRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.updateDraft(id, request)));
    }

    @PatchMapping("/{id}/send")
    @PreAuthorize("hasAnyRole('OWNER', 'SALES')")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> send(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Đã gửi nhà cung cấp", service.sendToSupplier(id)));
    }

    @PatchMapping("/{id}/confirm-receiving")
    @PreAuthorize("hasAnyRole('OWNER', 'SALES')")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> confirmReceiving(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Đã xác nhận nhận hàng", service.confirmReceiving(id)));
    }

    @PostMapping("/{id}/surplus")
    @PreAuthorize("hasAnyRole('OWNER', 'SALES')")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> createSurplusOrder(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Đã tạo đơn thặng dư", service.createSurplusOrder(id)));
    }

    @PostMapping("/{id}/shortage")
    @PreAuthorize("hasAnyRole('OWNER', 'SALES')")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> createShortageOrder(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Đã tạo đơn bổ sung hàng thiếu", service.createShortageOrder(id)));
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('OWNER', 'SALES')")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(service.cancel(id)));
    }

    @PatchMapping("/{id}/inspect/save")
    @PreAuthorize("hasAnyRole('OWNER', 'SALES')")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> saveInspection(
            @PathVariable UUID id,
            @RequestBody @Valid List<InspectionItemRequest> items) {
        return ResponseEntity.ok(ApiResponse.success("Đã lưu kết quả kiểm tra", service.saveInspection(id, items)));
    }

    @PatchMapping("/{id}/inspect/complete")
    @PreAuthorize("hasAnyRole('OWNER', 'SALES')")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> completeInspection(
            @PathVariable UUID id,
            @RequestBody @Valid List<InspectionItemRequest> items) {
        return ResponseEntity.ok(ApiResponse.success("Hoàn thành kiểm tra", service.completeInspection(id, items)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'SALES', 'OPERATIONS')")
    public ResponseEntity<ApiResponse<PageResponse<PurchaseOrderResponse>>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) PurchaseOrderStatus status) {
        return ResponseEntity.ok(ApiResponse.success(service.getAll(page, size, status)));
    }

    @GetMapping("/statistics")
    @PreAuthorize("hasAnyRole('OWNER', 'SALES', 'OPERATIONS')")
    public ResponseEntity<ApiResponse<Map<String, Long>>> statistics() {
        return ResponseEntity.ok(ApiResponse.success(service.getStatistics()));
    }

    @GetMapping("/form-options")
    @PreAuthorize("hasAnyRole('OWNER', 'SALES')")
    public ResponseEntity<ApiResponse<PurchaseOrderFormOptionsResponse>> formOptions() {
        return ResponseEntity.ok(ApiResponse.success(service.getFormOptions()));
    }

    @GetMapping("/next-code")
    @PreAuthorize("hasAnyRole('OWNER', 'SALES')")
    public ResponseEntity<ApiResponse<Map<String, String>>> nextCode() {
        return ResponseEntity.ok(ApiResponse.success(Map.of("orderCode", service.generateOrderCode())));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'SALES', 'OPERATIONS')")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(service.getById(id)));
    }
}
