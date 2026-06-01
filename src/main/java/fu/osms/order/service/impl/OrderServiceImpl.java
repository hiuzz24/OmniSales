package fu.osms.order.service.impl;

import fu.osms.channel.entity.Channel;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.common.dto.PageResponse;
import fu.osms.order.dto.request.OrderRequest;
import fu.osms.order.dto.response.OrderResponse;
import fu.osms.order.entity.Order;
import fu.osms.order.enums.OrderStatus;
import fu.osms.order.mapper.OrderMapper;
import fu.osms.order.repository.OrderRepository;
import fu.osms.order.service.OrderService;
import fu.osms.shop.entity.Shop;
import fu.osms.shop.repository.ShopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final ShopRepository shopRepository;
    private final ChannelRepository channelRepository;
    private final OrderMapper orderMapper;

    @Override
    @Transactional
    public OrderResponse create(OrderRequest request) {
        Shop shop = shopRepository.findById(request.getShopId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy shop"));
        Order order = orderMapper.toEntity(request);
        order.setShop(shop);
        if (request.getChannelId() != null) {
            Channel channel = channelRepository.findById(request.getChannelId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy kênh bán hàng"));
            order.setChannel(channel);
        }
        order.setStatusChangedAt(OffsetDateTime.now());
        return orderMapper.toResponse(orderRepository.save(order));
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getById(UUID id) {
        return orderRepository.findById(id)
                .map(orderMapper::toResponseWithItems)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getByShopId(UUID shopId, int page, int size) {
        Page<Order> pageResult = orderRepository.findByShopId(shopId, PageRequest.of(page, size));
        return toPageResponse(pageResult, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getByStatus(UUID shopId, OrderStatus status, int page, int size) {
        Page<Order> pageResult = orderRepository.findByShopIdAndStatus(shopId, status, PageRequest.of(page, size));
        return toPageResponse(pageResult, page, size);
    }

    @Override
    @Transactional
    public OrderResponse updateStatus(UUID id, OrderStatus status) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng: " + id));
        order.setStatus(status);
        order.setStatusChangedAt(OffsetDateTime.now());
        return orderMapper.toResponse(orderRepository.save(order));
    }

    @Override
    @Transactional
    public OrderResponse update(UUID id, OrderRequest request) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng: " + id));
        orderMapper.updateEntityFromRequest(request, order);
        return orderMapper.toResponse(orderRepository.save(order));
    }

    @Override
    @Transactional
    public void cancel(UUID id, String reason) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng: " + id));
        if (order.getStatus() == OrderStatus.SHIPPED || order.getStatus() == OrderStatus.DELIVERED) {
            throw new IllegalStateException("Không thể huỷ đơn hàng ở trạng thái: " + order.getStatus());
        }
        order.setStatus(OrderStatus.CANCELLED);
        order.setNote(reason);
        order.setStatusChangedAt(OffsetDateTime.now());
        orderRepository.save(order);
    }

    private PageResponse<OrderResponse> toPageResponse(Page<Order> p, int page, int size) {
        return PageResponse.<OrderResponse>builder()
                .content(p.getContent().stream().map(orderMapper::toResponse).toList())
                .page(page).size(size)
                .totalElements(p.getTotalElements())
                .totalPages(p.getTotalPages())
                .first(p.isFirst()).last(p.isLast())
                .build();
    }
}
