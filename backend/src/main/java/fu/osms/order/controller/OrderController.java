package fu.osms.order.controller;

import fu.osms.audit.dto.response.AuditLogResponse;
import fu.osms.channel.service.ChannelService;
import fu.osms.common.dto.ApiResponse;
import fu.osms.common.dto.PageResponse;
import fu.osms.order.dto.request.CancelOrderRequest;
import fu.osms.order.dto.request.OrderBatchCancelRequest;
import fu.osms.order.dto.request.RejectBuyerCancellationRequest;
import fu.osms.order.dto.response.OrderBatchCancelItemResponse;
import fu.osms.order.dto.response.CancelReasonResponse;
import fu.osms.order.dto.response.OrderResponse;
import fu.osms.order.dto.response.OrderShippingLabelResponse;
import fu.osms.order.dto.response.OrderStats;
import fu.osms.order.dto.response.UncustomerdCountResponse;
import fu.osms.order.dto.response.BuyerCancellationResponse;
import fu.osms.order.enums.OrderStatus;
import fu.osms.order.service.OrderService;
import fu.osms.order.service.OrderShippingLabelService;
import fu.osms.order.service.OrderBatchCancelService;
import fu.osms.sync.tiktok.TikTokBuyerCancellationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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
    private final OrderShippingLabelService orderShippingLabelService;
    private final ChannelService channelService;
    private final OrderBatchCancelService orderBatchCancelService;
    private final TikTokBuyerCancellationService buyerCancellationService;

    /** Trả về một đơn đã import cùng các dòng sản phẩm cho màn chi tiết. */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'SALES', 'OPERATIONS')")
    public ResponseEntity<ApiResponse<OrderResponse>> getById(@PathVariable UUID id) {
        OrderResponse response = orderService.getById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** Tìm đơn đã import theo platform, trạng thái, khách hàng, từ khóa và ngày. */
    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'SALES', 'OPERATIONS')")
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> getOrders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) UUID channelId,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Boolean waitingStockExpired,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        OffsetDateTime fromDt = from != null ? from.atStartOfDay().atOffset(OffsetDateTime.now().getOffset()) : null;
        OffsetDateTime toDt = to != null ? to.atTime(LocalTime.MAX).atOffset(OffsetDateTime.now().getOffset()) : null;

        PageResponse<OrderResponse> result = orderService.getFiltered(
                status, channelId, keyword, fromDt, toDt, customerId, waitingStockExpired, page, size);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /** Tải lịch sử audit của một đơn hàng. */
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

    @GetMapping("/uncustomerd-count")
    public ResponseEntity<ApiResponse<UncustomerdCountResponse>> getUncustomerdCount() {
        long count = orderService.countOrdersWithoutCustomer();
        return ResponseEntity.ok(ApiResponse.success(
                UncustomerdCountResponse.builder().count(count).build()));
    }

    /** Áp dụng chuyển trạng thái hợp lệ và đẩy lên platform khi cần. */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('OWNER', 'SALES')")
    public ResponseEntity<ApiResponse<OrderResponse>> updateStatus(@PathVariable UUID id,
                                                                   @RequestParam OrderStatus status) {
        OrderResponse response = orderService.updateStatus(id, status);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái thành công", response));
    }

    /** Xác nhận thủ công một order đã có lại tồn kho và chuyển thẳng sang CONFIRMED. */
    @PostMapping("/{id}/waiting-stock/confirm")
    @PreAuthorize("hasAnyRole('OWNER', 'SALES')")
    public ResponseEntity<ApiResponse<OrderResponse>> confirmWaitingStock(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                "Đã giữ tồn và xác nhận đơn hàng", orderService.confirmWaitingStock(id)));
    }

    @PatchMapping("/{id}/payment-status")
    public ResponseEntity<ApiResponse<OrderResponse>> updatePaymentStatus(@PathVariable UUID id,
                                                                          @RequestParam String paymentStatus) {
        OrderResponse response = orderService.updatePaymentStatus(id, fu.osms.order.enums.PaymentStatus.valueOf(paymentStatus));
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái thanh toán thành công", response));
    }

    /** Hủy đơn thông qua luồng hủy riêng của từng platform. */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('OWNER', 'SALES')")
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

    /** Lấy danh sách lý do hủy được platform hỗ trợ cho đơn đã chọn. */
    @GetMapping("/{id}/cancel-reasons")
    @PreAuthorize("hasAnyRole('OWNER', 'SALES')")
    public ResponseEntity<ApiResponse<List<CancelReasonResponse>>> getCancelReasons(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getCancelReasons(id)));
    }

    /** Yêu cầu phiếu vận chuyển chính thức từ sàn mà không đổi trạng thái đơn. */
    @PostMapping("/{id}/shipping-label")
    @PreAuthorize("hasAnyRole('OWNER', 'SALES', 'OPERATIONS')")
    public ResponseEntity<ApiResponse<OrderShippingLabelResponse>> createShippingLabel(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(orderShippingLabelService.createLabel(id)));
    }

    /** Hủy độc lập từng đơn chờ hàng bằng lý do hết hàng hợp lệ của platform. */
    @PostMapping("/waiting-stock/cancel-batch")
    @PreAuthorize("hasAnyRole('OWNER', 'SALES')")
    public ResponseEntity<ApiResponse<List<OrderBatchCancelItemResponse>>> cancelWaitingStockBatch(
            @Valid @RequestBody OrderBatchCancelRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                orderBatchCancelService.cancelWaitingStock(request.orderIds())));
    }

    /** Đọc yêu cầu hủy do Buyer tạo và quyền quyết định hiện tại từ TikTok. */
    @GetMapping("/{id}/buyer-cancellation")
    @PreAuthorize("hasAnyRole('OWNER', 'SALES', 'OPERATIONS')")
    public ResponseEntity<ApiResponse<BuyerCancellationResponse>> getBuyerCancellation(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(buyerCancellationService.get(id)));
    }

    /** Chấp thuận yêu cầu hủy của Buyer; trạng thái local chỉ hủy khi TikTok báo COMPLETE. */
    @PostMapping("/{id}/buyer-cancellation/approve")
    @PreAuthorize("hasAnyRole('OWNER', 'SALES')")
    public ResponseEntity<ApiResponse<BuyerCancellationResponse>> approveBuyerCancellation(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(buyerCancellationService.approve(id)));
    }

    /** Từ chối yêu cầu hủy của Buyer bằng reason TikTok đang cho phép. */
    @PostMapping("/{id}/buyer-cancellation/reject")
    @PreAuthorize("hasAnyRole('OWNER', 'SALES')")
    public ResponseEntity<ApiResponse<BuyerCancellationResponse>> rejectBuyerCancellation(
            @PathVariable UUID id,
            @Valid @RequestBody RejectBuyerCancellationRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                buyerCancellationService.reject(id, request.reasonCode(), request.comment())));
    }
}
