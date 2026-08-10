package fu.osms.sync.order.shipping.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.order.dto.response.OrderShippingLabelResponse;
import fu.osms.order.enums.OrderStatus;
import fu.osms.sync.lazada.service.LazadaAuthorizedApiClient;
import fu.osms.sync.order.shipping.PlatformShippingLabelGateway;
import fu.osms.sync.webhook.WebhookPayloadUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class LazadaShippingLabelGateway implements PlatformShippingLabelGateway {

    private static final int MAX_PACKAGES = 20;

    private final LazadaAuthorizedApiClient lazadaApiClient;
    private final ObjectMapper objectMapper;

    @Override
    public PlatformType getPlatform() {
        return PlatformType.LAZADA;
    }

    @Override
    public OrderShippingLabelResponse createLabel(OrderShippingLabelContext context) {
        if (context.status() != OrderStatus.SHIPPED) {
            throw new AppException(ErrorCode.ORDER_SHIPPING_LABEL_NOT_READY);
        }

        List<Map<String, Object>> items = fetchOrderItems(context);
        if (items.stream().anyMatch(this::isSellerOwnFleet)) {
            throw new AppException(ErrorCode.ORDER_SHIPPING_LABEL_UNSUPPORTED,
                    "Lazada không hỗ trợ PrintAWB cho đơn do người bán tự vận chuyển");
        }

        Set<String> packageIds = new LinkedHashSet<>();
        items.stream()
                .map(item -> WebhookPayloadUtils.text(WebhookPayloadUtils.firstPresent(
                        item, "package_id", "packageId")))
                .filter(this::hasText)
                .forEach(packageIds::add);
        if (packageIds.isEmpty()) {
            throw new AppException(ErrorCode.ORDER_SHIPPING_LABEL_PACKAGE_MISSING);
        }
        if (packageIds.size() > MAX_PACKAGES) {
            throw new AppException(ErrorCode.ORDER_SHIPPING_LABEL_PLATFORM_ERROR,
                    "Lazada chỉ hỗ trợ in tối đa 20 kiện trong một lần");
        }

        String request = toJson(Map.of(
                "doc_type", "PDF",
                "packages", packageIds.stream().map(id -> Map.of("package_id", id)).toList()
        ));
        String rawResponse = lazadaApiClient.executePost(
                context.channel().getId(),
                "/order/package/document/get",
                Map.of("getDocumentReq", request)
        );
        Map<String, Object> response = WebhookPayloadUtils.parseObject(
                rawResponse, "Lazada shipping document response is invalid");
        requireLazadaSuccess(response);
        String documentUrl = documentUrl(response);
        if (!hasText(documentUrl)) {
            throw new AppException(ErrorCode.ORDER_SHIPPING_LABEL_PLATFORM_ERROR,
                    "Lazada không trả về đường dẫn phiếu vận chuyển");
        }
        return new OrderShippingLabelResponse(
                PlatformType.LAZADA, documentUrl, "PDF", packageIds.size());
    }

    private List<Map<String, Object>> fetchOrderItems(OrderShippingLabelContext context) {
        String response = lazadaApiClient.executeGet(
                context.channel().getId(),
                "/order/items/get",
                Map.of("order_id", context.externalOrderId())
        );
        Map<String, Object> root = WebhookPayloadUtils.parseObject(
                response, "Lazada order items response is invalid");
        requireLazadaSuccess(root);
        Object data = root.get("data");
        if (!(data instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> items = new ArrayList<>();
        list.stream().filter(Map.class::isInstance)
                .map(WebhookPayloadUtils::copyMap)
                .forEach(items::add);
        return items;
    }

    private boolean isSellerOwnFleet(Map<String, Object> item) {
        String value = WebhookPayloadUtils.text(WebhookPayloadUtils.firstPresent(
                item, "delivery_option_sof", "deliveryOptionSof"));
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }

    private void requireLazadaSuccess(Map<String, Object> response) {
        String code = WebhookPayloadUtils.text(response.get("code"));
        if (code == null || "0".equals(code)) {
            return;
        }
        String message = WebhookPayloadUtils.text(
                WebhookPayloadUtils.firstPresent(response, "message", "msg"));
        String normalized = message == null ? "" : message.toUpperCase(Locale.ROOT);
        if (normalized.contains("SOF") || normalized.contains("DBS")
                || normalized.contains("DELIVERY BY SELLER")) {
            throw new AppException(ErrorCode.ORDER_SHIPPING_LABEL_UNSUPPORTED,
                    "Lazada không hỗ trợ PrintAWB cho phương thức vận chuyển của đơn này");
        }
        throw new AppException(ErrorCode.ORDER_SHIPPING_LABEL_PLATFORM_ERROR);
    }

    private String documentUrl(Map<String, Object> response) {
        Map<String, Object> result = WebhookPayloadUtils.copyMap(response.get("result"));
        Map<String, Object> data = WebhookPayloadUtils.copyMap(result.get("data"));
        if (data.isEmpty()) {
            data = WebhookPayloadUtils.copyMap(response.get("data"));
        }
        return WebhookPayloadUtils.text(WebhookPayloadUtils.firstPresent(data, "pdf_url", "pdfUrl"));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new AppException(ErrorCode.ORDER_SHIPPING_LABEL_PLATFORM_ERROR);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
