package fu.osms.order.controller;

import fu.osms.audit.dto.response.AuditLogResponse;
import fu.osms.channel.service.ChannelService;
import fu.osms.common.dto.ApiResponse;
import fu.osms.common.dto.PageResponse;
import fu.osms.order.dto.request.CancelOrderRequest;
import fu.osms.order.dto.request.OrderRequest;
import fu.osms.order.dto.response.CancelReasonResponse;
import fu.osms.order.dto.response.OrderResponse;
import fu.osms.order.dto.response.OrderStats;
import fu.osms.order.enums.OrderStatus;
import fu.osms.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final ChannelService channelService;

    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> create(@Valid @RequestBody OrderRequest request) {
        OrderResponse response = orderService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo đơn hàng thành công", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getById(@PathVariable UUID id) {
        OrderResponse response = orderService.getById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> getOrders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) UUID channelId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        OffsetDateTime fromDt = from != null ? from.atStartOfDay().atOffset(OffsetDateTime.now().getOffset()) : null;
        OffsetDateTime toDt = to != null ? to.atTime(LocalTime.MAX).atOffset(OffsetDateTime.now().getOffset()) : null;

        PageResponse<OrderResponse> result = orderService.getFiltered(
                status, channelId, keyword, fromDt, toDt, page, size);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<ApiResponse<PageResponse<AuditLogResponse>>> getOrderHistory(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResponse<AuditLogResponse> result = orderService.getOrderHistory(id, page, size);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<OrderStats>> getStats() {
        OrderStats stats = orderService.getStats();
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('OWNER', 'SALES')")
    public ResponseEntity<ApiResponse<OrderResponse>> updateStatus(@PathVariable UUID id,
                                                                   @RequestParam OrderStatus status) {
        OrderResponse response = orderService.updateStatus(id, status);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái thành công", response));
    }

    @PatchMapping("/{id}/payment-status")
    public ResponseEntity<ApiResponse<OrderResponse>> updatePaymentStatus(@PathVariable UUID id,
                                                                          @RequestParam String paymentStatus) {
        OrderResponse response = orderService.updatePaymentStatus(id, fu.osms.order.enums.PaymentStatus.valueOf(paymentStatus));
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái thanh toán thành công", response));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> update(@PathVariable UUID id,
                                                            @Valid @RequestBody OrderRequest request) {
        OrderResponse response = orderService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật đơn hàng thành công", response));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancel(@PathVariable UUID id,
                                                    @RequestBody(required = false) CancelOrderRequest request,
                                                    @RequestParam(required = false) String reason) {
        CancelOrderRequest cancelRequest = request != null ? request : new CancelOrderRequest();
        if ((cancelRequest.getReason() == null || cancelRequest.getReason().isBlank()) && reason != null) {
            cancelRequest.setReason(reason);
        }
        orderService.cancel(id, cancelRequest);
        return ResponseEntity.ok(ApiResponse.success("Hủy đơn hàng thành công", null));
    }

    @GetMapping("/{id}/cancel-reasons")
    public ResponseEntity<ApiResponse<List<CancelReasonResponse>>> getCancelReasons(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getCancelReasons(id)));
    }
}
