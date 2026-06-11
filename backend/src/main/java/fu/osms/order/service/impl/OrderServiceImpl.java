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
    private final ChannelRepository channelRepository;
    private final OrderMapper orderMapper;

    @Override
    @Transactional
    public OrderResponse create(OrderRequest request) {
        throw new UnsupportedOperationException("Chưa code");
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getById(UUID id) {
        throw new UnsupportedOperationException("Chưa code");
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getAll(int page, int size) {
        throw new UnsupportedOperationException("Chưa code");
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getByStatus(OrderStatus status, int page, int size) {
        throw new UnsupportedOperationException("Chưa code");
    }

    @Override
    @Transactional
    public OrderResponse updateStatus(UUID id, OrderStatus status) {
        throw new UnsupportedOperationException("Chưa code");
    }

    @Override
    @Transactional
    public OrderResponse update(UUID id, OrderRequest request) {
        throw new UnsupportedOperationException("Chưa code");
    }

    @Override
    @Transactional
    public void cancel(UUID id, String reason) {
        throw new UnsupportedOperationException("Chưa code");
    }

    private PageResponse<OrderResponse> toPageResponse(Page<Order> p, int page, int size) {
        throw new UnsupportedOperationException("Chưa code");
    }
}
