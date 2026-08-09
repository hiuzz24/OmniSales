package fu.osms.order.service.impl;

import fu.osms.common.enums.PlatformType;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.order.dto.response.OrderShippingLabelResponse;
import fu.osms.order.entity.Order;
import fu.osms.order.repository.OrderRepository;
import fu.osms.order.service.OrderShippingLabelService;
import fu.osms.sync.order.shipping.PlatformShippingLabelGateway;
import fu.osms.sync.order.shipping.PlatformShippingLabelGateway.OrderShippingLabelContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class OrderShippingLabelServiceImpl implements OrderShippingLabelService {

    private final OrderRepository orderRepository;
    private final Map<PlatformType, PlatformShippingLabelGateway> gateways;

    public OrderShippingLabelServiceImpl(OrderRepository orderRepository,
                                         List<PlatformShippingLabelGateway> gateways) {
        this.orderRepository = orderRepository;
        this.gateways = new EnumMap<>(PlatformType.class);
        gateways.forEach(gateway -> this.gateways.put(gateway.getPlatform(), gateway));
    }

    @Override
    public OrderShippingLabelResponse createLabel(UUID orderId) {
        Order order = orderRepository.findByIdWithChannel(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        PlatformShippingLabelGateway gateway = gateways.get(order.getPlatform());
        if (gateway == null || order.getChannel() == null) {
            throw new AppException(ErrorCode.ORDER_SHIPPING_LABEL_UNSUPPORTED);
        }

        OrderShippingLabelContext context = new OrderShippingLabelContext(
                order.getId(),
                order.getExternalOrderId(),
                order.getPlatform(),
                order.getStatus(),
                order.getChannel(),
                order.getPlatformMetadata() == null ? Map.of() : new HashMap<>(order.getPlatformMetadata())
        );

        try {
            OrderShippingLabelResponse response = gateway.createLabel(context);
            validateDocumentUrl(response.documentUrl());
            return response;
        } catch (AppException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("[OrderShippingLabel] Platform request failed orderId={} platform={} cause={}",
                    orderId, order.getPlatform(), exception.getClass().getSimpleName());
            throw new AppException(ErrorCode.ORDER_SHIPPING_LABEL_PLATFORM_ERROR);
        }
    }

    private void validateDocumentUrl(String documentUrl) {
        try {
            URI uri = URI.create(documentUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                throw new IllegalArgumentException("Shipping document URL must use HTTPS");
            }
        } catch (RuntimeException exception) {
            throw new AppException(ErrorCode.ORDER_SHIPPING_LABEL_PLATFORM_ERROR,
                    "Sàn trả về đường dẫn phiếu vận chuyển không hợp lệ");
        }
    }
}
