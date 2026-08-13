package fu.osms.orderreturn.controller;

import fu.osms.common.dto.ApiResponse;
import fu.osms.common.dto.PageResponse;
import fu.osms.orderreturn.dto.request.OrderReturnInspectionRequest;
import fu.osms.orderreturn.dto.request.OrderReturnRejectRequest;
import fu.osms.orderreturn.dto.response.OrderReturnResponse;
import fu.osms.orderreturn.dto.response.OrderReturnRejectOptionsResponse;
import fu.osms.orderreturn.service.OrderReturnService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/order-returns")
@RequiredArgsConstructor
public class OrderReturnController {

    private final OrderReturnService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'SALES', 'OPERATIONS')")
    public ResponseEntity<ApiResponse<PageResponse<OrderReturnResponse>>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success(service.getAll(page, size)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'SALES', 'OPERATIONS')")
    public ResponseEntity<ApiResponse<OrderReturnResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(service.getById(id)));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('OWNER', 'SALES')")
    public ResponseEntity<ApiResponse<OrderReturnResponse>> approve(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(service.approve(id)));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('OWNER', 'SALES')")
    public ResponseEntity<ApiResponse<OrderReturnResponse>> reject(
            @PathVariable UUID id,
            @Valid @RequestBody OrderReturnRejectRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.reject(id, request)));
    }

    @GetMapping("/{id}/reject-options")
    @PreAuthorize("hasAnyRole('OWNER', 'SALES')")
    public ResponseEntity<ApiResponse<OrderReturnRejectOptionsResponse>> getRejectOptions(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(service.getRejectOptions(id)));
    }

    @PostMapping("/{id}/inspect")
    @PreAuthorize("hasAnyRole('OWNER', 'OPERATIONS')")
    public ResponseEntity<ApiResponse<OrderReturnResponse>> inspect(
            @PathVariable UUID id,
            @Valid @RequestBody OrderReturnInspectionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.inspect(id, request)));
    }

    @PostMapping("/{id}/refresh")
    @PreAuthorize("hasAnyRole('OWNER', 'SALES', 'OPERATIONS')")
    public ResponseEntity<ApiResponse<OrderReturnResponse>> refresh(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(service.refresh(id)));
    }

    @PostMapping("/{id}/check-action")
    @PreAuthorize("hasAnyRole('OWNER', 'SALES', 'OPERATIONS')")
    public ResponseEntity<ApiResponse<OrderReturnResponse>> checkAction(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(service.checkAction(id)));
    }

    @PostMapping("/{id}/retry-action")
    @PreAuthorize("hasAnyRole('OWNER', 'SALES', 'OPERATIONS')")
    public ResponseEntity<ApiResponse<OrderReturnResponse>> retryAction(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(service.retryAction(id)));
    }

    @PostMapping("/{id}/retry-stock")
    @PreAuthorize("hasAnyRole('OWNER', 'OPERATIONS')")
    public ResponseEntity<ApiResponse<OrderReturnResponse>> retryStock(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(service.retryStock(id)));
    }
}
