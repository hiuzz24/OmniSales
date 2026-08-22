package fu.osms.order.service.impl;

import fu.osms.common.enums.PlatformType;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.order.dto.request.CancelOrderRequest;
import fu.osms.order.dto.response.CancelReasonResponse;
import fu.osms.order.dto.response.OrderBatchCancelItemResponse;
import fu.osms.order.entity.Order;
import fu.osms.order.enums.OrderStatus;
import fu.osms.order.enums.ShopifyCancelReason;
import fu.osms.order.repository.OrderRepository;
import fu.osms.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderBatchCancelExecutorServiceImpl {
    private final OrderRepository orderRepository;
    private final OrderService orderService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OrderBatchCancelItemResponse cancelOne(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        if (order.getStatus() != OrderStatus.WAITING_STOCK) {
            throw new AppException(ErrorCode.ORDER_STATUS_INVALID_TRANSITION,
                    "Chỉ có thể hủy hàng loạt đơn đang Chờ hàng");
        }
        CancelOrderRequest request = requestFor(order);
        orderService.cancel(orderId, request);
        Order current = orderRepository.findById(orderId).orElseThrow();
        String message = current.getStatus() == OrderStatus.CANCELLED
                ? "Đã hủy đơn do hết hàng"
                : "Đã gửi yêu cầu hủy lên sàn, đang chờ xác nhận";
        return new OrderBatchCancelItemResponse(orderId, true, message);
    }

    private CancelOrderRequest requestFor(Order order) {
        CancelOrderRequest request = new CancelOrderRequest();
        request.setReason("Hết hàng");
        if (order.getPlatform() == PlatformType.SHOPIFY) {
            request.setShopifyReason(ShopifyCancelReason.INVENTORY);
            request.setEmail(true);
            request.setRefund(true);
            request.setRestock(false);
            return request;
        }
        CancelReasonResponse reason = selectOutOfStockReason(orderService.getCancelReasons(order.getId()));
        if (reason == null) {
            throw new AppException(ErrorCode.CONFLICT,
                    "Sàn hiện không cung cấp lý do hủy do hết hàng cho đơn này");
        }
        if (order.getPlatform() == PlatformType.TIKTOK) request.setTikTokReason(reason.getId());
        if (order.getPlatform() == PlatformType.LAZADA) request.setReasonId(reason.getId());
        return request;
    }

    private CancelReasonResponse selectOutOfStockReason(List<CancelReasonResponse> reasons) {
        return reasons.stream().filter(reason -> {
            String value = normalize(String.valueOf(reason.getId()) + " " + reason.getName());
            return value.contains("out_of_stock") || value.contains("out of stock")
                    || value.contains("het hang") || value.contains("inventory")
                    || value.contains("stock unavailable");
        }).findFirst().orElse(null);
    }

    private String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
