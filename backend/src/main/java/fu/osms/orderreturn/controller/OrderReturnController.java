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

    /** Liệt kê yêu cầu trả hàng từ platform cho màn nghiệp vụ. */
    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'SALES', 'OPERATIONS')")
    public ResponseEntity<ApiResponse<PageResponse<OrderReturnResponse>>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success(service.getAll(page, size)));
    }

    /** Trả về đầy đủ vòng đời, số lượng và trạng thái action của phiếu trả hàng. */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'SALES', 'OPERATIONS')")
    public ResponseEntity<ApiResponse<OrderReturnResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(service.getById(id)));
    }

    /** Duyệt yêu cầu trả hàng đang chờ qua gateway của sàn. */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('OWNER', 'SALES')")
    public ResponseEntity<ApiResponse<OrderReturnResponse>> approve(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(service.approve(id)));
    }

    /** Từ chối yêu cầu trả hàng bằng lý do hợp lệ của platform. */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('OWNER', 'SALES')")
    public ResponseEntity<ApiResponse<OrderReturnResponse>> reject(
            @PathVariable UUID id,
            @Valid @RequestBody OrderReturnRejectRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.reject(id, request)));
    }

    /** Tải lý do từ chối hợp lệ từ sàn khi cần. */
    @GetMapping("/{id}/reject-options")
    @PreAuthorize("hasAnyRole('OWNER', 'SALES')")
    public ResponseEntity<ApiResponse<OrderReturnRejectOptionsResponse>> getRejectOptions(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(service.getRejectOptions(id)));
    }

    /** Lưu số lượng kiểm hàng tại kho trước khi bắt đầu xử lý trên platform. */
    @PostMapping("/{id}/inspect")
    @PreAuthorize("hasAnyRole('OWNER', 'OPERATIONS')")
    public ResponseEntity<ApiResponse<OrderReturnResponse>> inspect(
            @PathVariable UUID id,
            @Valid @RequestBody OrderReturnInspectionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.inspect(id, request)));
    }

    /** Lấy snapshot mới nhất từ platform mà không lặp lại action. */
    @PostMapping("/{id}/refresh")
    @PreAuthorize("hasAnyRole('OWNER', 'SALES', 'OPERATIONS')")
    public ResponseEntity<ApiResponse<OrderReturnResponse>> refresh(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(service.refresh(id)));
    }

    /** Xác định action UNKNOWN bằng cách kiểm tra side effect trên platform. */
    @PostMapping("/{id}/check-action")
    @PreAuthorize("hasAnyRole('OWNER', 'SALES', 'OPERATIONS')")
    public ResponseEntity<ApiResponse<OrderReturnResponse>> checkAction(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(service.checkAction(id)));
    }

    /** Chỉ thử lại action đã được chứng minh an toàn và giữ nguyên request identity. */
    @PostMapping("/{id}/retry-action")
    @PreAuthorize("hasAnyRole('OWNER', 'SALES', 'OPERATIONS')")
    public ResponseEntity<ApiResponse<OrderReturnResponse>> retryAction(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(service.retryAction(id)));
    }

    /** Thử lại nhập kho nội bộ idempotent sau lỗi nhập kho trước đó. */
    @PostMapping("/{id}/retry-stock")
    @PreAuthorize("hasAnyRole('OWNER', 'OPERATIONS')")
    public ResponseEntity<ApiResponse<OrderReturnResponse>> retryStock(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(service.retryStock(id)));
    }
}
