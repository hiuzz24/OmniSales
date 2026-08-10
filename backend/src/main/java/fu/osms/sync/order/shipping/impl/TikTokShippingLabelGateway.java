package fu.osms.sync.order.shipping.impl;

import fu.osms.common.enums.PlatformType;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.order.dto.response.OrderShippingLabelResponse;
import fu.osms.order.enums.OrderStatus;
import fu.osms.sync.order.shipping.PlatformShippingLabelGateway;
import fu.osms.sync.tiktok.TikTokOrderApiService;
import fu.osms.sync.webhook.WebhookPayloadUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class TikTokShippingLabelGateway implements PlatformShippingLabelGateway {

    private static final Set<String> UNSUPPORTED_CODES = Set.of("11034002", "21008017", "21008043");
    private static final Set<String> NOT_READY_CODES = Set.of(
            "11006010", "11034023", "11034037", "21008109", "21021010",
            "21023022", "21023034", "21023035", "21023046", "21023059",
            "21042102", "21042104"
    );

    private final TikTokOrderApiService tikTokOrderApiService;

    @Override
    public PlatformType getPlatform() {
        return PlatformType.TIKTOK;
    }

    @Override
    public OrderShippingLabelResponse createLabel(OrderShippingLabelContext context) {
        if (context.status() != OrderStatus.SHIPPED) {
            throw new AppException(ErrorCode.ORDER_SHIPPING_LABEL_NOT_READY);
        }

        String packageId = storedPackageId(context.platformMetadata());
        if (!hasText(packageId)) {
            Map<String, Object> detail = tikTokOrderApiService.getOrderDetail(
                    context.channel(), context.externalOrderId());
            requireTikTokShipping(detail);
            Set<String> packageIds = packageIds(detail);
            if (packageIds.isEmpty()) {
                throw new AppException(ErrorCode.ORDER_SHIPPING_LABEL_PACKAGE_MISSING);
            }
            if (packageIds.size() != 1) {
                throw new AppException(ErrorCode.ORDER_SHIPPING_LABEL_MULTIPLE_UNSUPPORTED);
            }
            packageId = packageIds.iterator().next();
        }

        TikTokOrderApiService.ShippingDocumentResult document = tikTokOrderApiService
                .getPackageShippingDocument(context.channel(), packageId);
        requireDocumentSuccess(document);
        if (!hasText(document.documentUrl())) {
            throw new AppException(ErrorCode.ORDER_SHIPPING_LABEL_PLATFORM_ERROR,
                    "TikTok không trả về đường dẫn phiếu vận chuyển");
        }
        return new OrderShippingLabelResponse(PlatformType.TIKTOK, document.documentUrl(), "PDF", 1);
    }

    private String storedPackageId(Map<String, Object> metadata) {
        Map<String, Object> tikTok = WebhookPayloadUtils.copyMap(metadata.get("tiktok"));
        return WebhookPayloadUtils.text(tikTok.get("packageId"));
    }

    private void requireTikTokShipping(Map<String, Object> detail) {
        String shippingType = WebhookPayloadUtils.text(
                WebhookPayloadUtils.firstPresent(detail, "shipping_type", "delivery_type"));
        if (!"TIKTOK".equalsIgnoreCase(shippingType)) {
            throw new AppException(ErrorCode.ORDER_SHIPPING_LABEL_UNSUPPORTED,
                    "Chỉ hỗ trợ in phiếu cho đơn do TikTok vận chuyển");
        }
    }

    private Set<String> packageIds(Map<String, Object> detail) {
        Set<String> result = new LinkedHashSet<>();
        maps(detail.get("packages")).forEach(item -> addIfPresent(result,
                WebhookPayloadUtils.text(WebhookPayloadUtils.firstPresent(item, "id", "package_id"))));
        maps(WebhookPayloadUtils.firstPresent(detail, "line_items", "order_line_items"))
                .forEach(item -> addIfPresent(result,
                        WebhookPayloadUtils.text(WebhookPayloadUtils.firstPresent(
                                item, "package_id", "packageId"))));
        return result;
    }

    private void requireDocumentSuccess(TikTokOrderApiService.ShippingDocumentResult document) {
        String code = document.code();
        if (code == null || "0".equals(code)) {
            return;
        }
        if (UNSUPPORTED_CODES.contains(code)) {
            throw new AppException(ErrorCode.ORDER_SHIPPING_LABEL_UNSUPPORTED,
                    "TikTok không hỗ trợ in phiếu cho phương thức vận chuyển của đơn này");
        }
        if (NOT_READY_CODES.contains(code)) {
            throw new AppException(ErrorCode.ORDER_SHIPPING_LABEL_NOT_READY,
                    "TikTok chưa tạo xong phiếu vận chuyển, vui lòng thử lại sau");
        }
        throw new AppException(ErrorCode.ORDER_SHIPPING_LABEL_PLATFORM_ERROR);
    }

    private List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        list.stream().filter(Map.class::isInstance)
                .map(WebhookPayloadUtils::copyMap)
                .forEach(result::add);
        return result;
    }

    private void addIfPresent(Set<String> values, String value) {
        if (hasText(value)) {
            values.add(value);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
