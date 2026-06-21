package fu.osms.order.service.impl;

import fu.osms.audit.dto.response.AuditLogResponse;
import fu.osms.audit.entity.AuditLog;
import fu.osms.audit.mapper.AuditLogMapper;
import fu.osms.audit.repository.AuditLogRepository;
import fu.osms.audit.service.AuditService;
import fu.osms.auth.entity.User;
import fu.osms.channel.entity.Channel;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.common.dto.PageResponse;
import fu.osms.customer.entity.Customer;
import fu.osms.customer.repository.CustomerRepository;
import fu.osms.order.dto.request.OrderItemRequest;
import fu.osms.order.dto.request.OrderRequest;
import fu.osms.order.dto.response.OrderItemResponse;
import fu.osms.order.dto.response.OrderResponse;
import fu.osms.order.dto.response.OrderStats;
import fu.osms.order.entity.Order;
import fu.osms.order.entity.OrderItem;
import fu.osms.order.enums.OrderStatus;
import fu.osms.order.enums.PaymentStatus;
import fu.osms.order.mapper.OrderItemMapper;
import fu.osms.order.mapper.OrderMapper;
import fu.osms.order.repository.OrderItemRepository;
import fu.osms.order.repository.OrderRepository;
import fu.osms.order.service.OrderService;
import fu.osms.order.spec.OrderSpec;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fu.osms.common.utils.SecurityUtils;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ChannelRepository channelRepository;
    private final CustomerRepository customerRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditService auditService;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final AuditLogMapper auditLogMapper;

    @Override
    @Transactional
    public OrderResponse create(OrderRequest request) {
        Order order = orderMapper.toEntity(request);

        if (request.getChannelId() != null) {
            Channel channel = channelRepository.findById(request.getChannelId())
                    .orElseThrow(() -> new EntityNotFoundException("Channel not found: " + request.getChannelId()));
            order.setChannel(channel);
        }

        if (request.getCustomerId() != null) {
            Customer customer = customerRepository.findById(request.getCustomerId())
                    .orElseThrow(() -> new EntityNotFoundException("Customer not found: " + request.getCustomerId()));
            order.setCustomer(customer);
        }

        order.setStatusChangedAt(OffsetDateTime.now());
        Order savedOrder = orderRepository.save(order);

        for (OrderItemRequest itemReq : request.getItems()) {
            OrderItem item = OrderItem.builder()
                    .order(savedOrder)
                    .sku(itemReq.getSku())
                    .name(itemReq.getName())
                    .quantity(itemReq.getQuantity())
                    .unitPrice(itemReq.getUnitPrice())
                    .discountAmount(itemReq.getDiscountAmount() != null ? itemReq.getDiscountAmount() : BigDecimal.ZERO)
                    .build();
            orderItemRepository.save(item);
        }

        var userOpt = SecurityUtils.getCurrentUser();
        UUID actorId = userOpt.map(User::getId).orElse(null);
        String actorEmail = userOpt.map(User::getEmail).orElse("system");
        auditService.record(actorId, actorEmail, "CREATE", "ORDER", savedOrder.getId(),
                savedOrder.getId().toString(), java.util.Map.of("status", savedOrder.getStatus().name()));

        return toResponseWithItems(savedOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getById(UUID id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + id));
        return toResponseWithItems(order);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getAll(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Order> orderPage = orderRepository.findAll(pageRequest);
        return toPageResponse(orderPage);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getByStatus(OrderStatus status, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Order> orderPage = orderRepository.findByStatus(status, pageRequest);
        return toPageResponse(orderPage);
    }

    @Override
    @Transactional
    public OrderResponse updateStatus(UUID id, OrderStatus status) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + id));

        OrderStatus oldStatus = order.getStatus();
        String oldPaymentStatus = order.getPaymentStatus();

        if (status == OrderStatus.CANCELLED) {
            order.setStatus(OrderStatus.CANCELLED);
        } else {
            order.setStatus(status);
        }

        if (status == OrderStatus.DELIVERED && "UNPAID".equals(order.getPaymentStatus())) {
            order.setPaymentStatus("PAID");
        }

        order.setStatusChangedAt(OffsetDateTime.now());

        Order savedOrder = orderRepository.save(order);

        // Ghi audit log
        var userOpt = SecurityUtils.getCurrentUser();
        UUID actorId = userOpt.map(User::getId).orElse(null);
        String actorEmail = userOpt.map(User::getEmail).orElse("system");

        boolean autoPaid = status == OrderStatus.DELIVERED && "PAID".equals(savedOrder.getPaymentStatus()) && "UNPAID".equals(oldPaymentStatus);
        if (autoPaid) {
            auditService.record(actorId, actorEmail, "STATUS_CHANGE", "ORDER", id,
                    id.toString(), java.util.Map.of("oldStatus", oldStatus.name(), "newStatus", status.name(), "autoPaymentStatus", "PAID"));
        } else {
            auditService.record(actorId, actorEmail, "STATUS_CHANGE", "ORDER", id,
                    id.toString(), java.util.Map.of("oldStatus", oldStatus.name(), "newStatus", status.name()));
        }

        return toResponseWithItems(savedOrder);
    }

    @Override
    @Transactional
    public OrderResponse updatePaymentStatus(UUID id, PaymentStatus paymentStatus) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + id));

        String oldStatus = order.getPaymentStatus();
        order.setPaymentStatus(paymentStatus.name());
        Order savedOrder = orderRepository.save(order);

        var userOpt = SecurityUtils.getCurrentUser();
        UUID actorId = userOpt.map(User::getId).orElse(null);
        String actorEmail = userOpt.map(User::getEmail).orElse("system");
        auditService.record(actorId, actorEmail, "PAYMENT_STATUS_CHANGE", "ORDER", id,
                id.toString(), java.util.Map.of("oldPaymentStatus", oldStatus, "newPaymentStatus", paymentStatus.name()));

        return toResponseWithItems(savedOrder);
    }

    @Override
    @Transactional
    public OrderResponse update(UUID id, OrderRequest request) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + id));

        orderMapper.updateEntityFromRequest(request, order);

        if (request.getChannelId() != null) {
            Channel channel = channelRepository.findById(request.getChannelId())
                    .orElseThrow(() -> new EntityNotFoundException("Channel not found: " + request.getChannelId()));
            order.setChannel(channel);
        }

        if (request.getCustomerId() != null) {
            Customer customer = customerRepository.findById(request.getCustomerId())
                    .orElseThrow(() -> new EntityNotFoundException("Customer not found: " + request.getCustomerId()));
            order.setCustomer(customer);
        }

        Order savedOrder = orderRepository.save(order);

        var userOpt = SecurityUtils.getCurrentUser();
        UUID actorId = userOpt.map(User::getId).orElse(null);
        String actorEmail = userOpt.map(User::getEmail).orElse("system");
        auditService.record(actorId, actorEmail, "UPDATE", "ORDER", id,
                savedOrder.getId().toString(), null);

        return toResponseWithItems(savedOrder);
    }

    @Override
    @Transactional
    public void cancel(UUID id, String reason) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + id));

        OrderStatus oldStatus = order.getStatus();
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelReason(reason);
        order.setStatusChangedAt(OffsetDateTime.now());
        orderRepository.save(order);

        var userOpt = SecurityUtils.getCurrentUser();
        UUID actorId = userOpt.map(User::getId).orElse(null);
        String actorEmail = userOpt.map(User::getEmail).orElse("system");
        auditService.record(actorId, actorEmail, "ORDER_CANCEL", "ORDER", id,
                order.getId().toString(), java.util.Map.of("oldStatus", oldStatus.name(), "newStatus", "CANCELLED", "reason", reason != null ? reason : ""));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getFiltered(OrderStatus status, UUID channelId, String keyword,
                                                    OffsetDateTime from, OffsetDateTime to,
                                                    int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        Specification<Order> spec = OrderSpec.withFilters(status, channelId, keyword, from, to);
        Page<Order> orderPage = orderRepository.findAll(spec, pageRequest);
        return toPageResponse(orderPage);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderStats getStats() {
        return new OrderStats(
                orderRepository.countAll(),
                orderRepository.countByStatus(OrderStatus.PENDING),
                orderRepository.countByStatus(OrderStatus.CONFIRMED),
                orderRepository.countByStatus(OrderStatus.PROCESSING),
                orderRepository.countByStatus(OrderStatus.SHIPPED),
                orderRepository.countByStatus(OrderStatus.DELIVERED),
                orderRepository.countByStatus(OrderStatus.CANCELLED),
                orderRepository.sumRevenueDelivered()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> getOrderHistory(UUID orderId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "performedAt"));
        Page<AuditLog> logPage = auditLogRepository.findByEntityTypeAndEntityId("ORDER", orderId, pageRequest);

        List<AuditLogResponse> content = logPage.getContent().stream()
                .map(auditLogMapper::toResponse)
                .toList();

        return PageResponse.<AuditLogResponse>builder()
                .content(content)
                .page(logPage.getNumber())
                .size(logPage.getSize())
                .totalElements(logPage.getTotalElements())
                .totalPages(logPage.getTotalPages())
                .first(logPage.isFirst())
                .last(logPage.isLast())
                .build();
    }

    private OrderResponse toResponseWithItems(Order order) {
        OrderResponse response = orderMapper.toResponseWithItems(order);

        List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
        List<OrderItemResponse> itemResponses = items.stream()
                .map(orderItemMapper::toResponse)
                .toList();
        response.setItems(itemResponses);

        return response;
    }

    private PageResponse<OrderResponse> toPageResponse(Page<Order> orderPage) {
        List<OrderResponse> content = orderPage.getContent().stream()
                .map(orderMapper::toResponse)
                .toList();

        return PageResponse.<OrderResponse>builder()
                .content(content)
                .page(orderPage.getNumber())
                .size(orderPage.getSize())
                .totalElements(orderPage.getTotalElements())
                .totalPages(orderPage.getTotalPages())
                .first(orderPage.isFirst())
                .last(orderPage.isLast())
                .build();
    }
}
