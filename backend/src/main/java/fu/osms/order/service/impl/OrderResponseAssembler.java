package fu.osms.order.service.impl;

import fu.osms.common.dto.PageResponse;
import fu.osms.order.dto.response.OrderItemResponse;
import fu.osms.order.dto.response.OrderResponse;
import fu.osms.order.entity.Order;
import fu.osms.order.mapper.OrderItemMapper;
import fu.osms.order.mapper.OrderMapper;
import fu.osms.order.repository.OrderItemRepository;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

final class OrderResponseAssembler {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderItemRepository orderItemRepository;

    OrderResponseAssembler(OrderMapper orderMapper, OrderItemMapper orderItemMapper,
                           OrderItemRepository orderItemRepository) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.orderItemRepository = orderItemRepository;
    }

    OrderResponse withItems(Order order) {
        OrderResponse response = orderMapper.toResponseWithItems(order);
        response.setItems(orderItemRepository.findByOrderId(order.getId()).stream()
                .map(orderItemMapper::toResponse).toList());
        return response;
    }

    PageResponse<OrderResponse> page(Page<Order> orderPage) {
        List<OrderResponse> content = orderPage.getContent().stream().map(orderMapper::toResponse).toList();
        attachItems(content);
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

    private void attachItems(List<OrderResponse> orders) {
        if (orders == null || orders.isEmpty()) return;
        List<UUID> orderIds = orders.stream().map(OrderResponse::getId).toList();
        Map<UUID, List<OrderItemResponse>> itemsByOrder = orderItemRepository.findByOrderIdIn(orderIds).stream()
                .collect(Collectors.groupingBy(item -> item.getOrder().getId(),
                        Collectors.mapping(orderItemMapper::toResponse, Collectors.toList())));
        orders.forEach(order -> order.setItems(itemsByOrder.getOrDefault(order.getId(), List.of())));
    }
}
