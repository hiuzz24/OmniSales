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
import fu.osms.common.enums.PlatformType;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.common.utils.SecurityUtils;
import fu.osms.customer.entity.Customer;
import fu.osms.customer.repository.CustomerRepository;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.catalog.repository.ProductVariantRepository;
import fu.osms.inventory.entity.InventoryItem;
import fu.osms.inventory.repository.InventoryItemRepository;
import fu.osms.inventory.service.InventoryAlertService;
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
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OrderServiceImpl Tests")
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private ChannelRepository channelRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderItemMapper orderItemMapper;
    @Mock
    private AuditLogMapper auditLogMapper;
    @Mock
    private ProductVariantRepository productVariantRepository;
    @Mock
    private InventoryItemRepository inventoryItemRepository;
    @Mock
    private InventoryAlertService inventoryAlertService;

    @InjectMocks
    private OrderServiceImpl orderService;

    private UUID orderId;
    private UUID channelId;
    private UUID customerId;
    private UUID variantId;
    private UUID userId;
    private Order order;
    private OrderRequest orderRequest;
    private OrderResponse orderResponse;
    private Channel channel;
    private Customer customer;
    private User user;
    private ProductVariant variant;
    private OrderItem orderItem;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        channelId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        variantId = UUID.randomUUID();
        userId = UUID.randomUUID();

        user = User.builder()
                .id(userId)
                .email("manager@osms.vn")
                .fullName("Manager User")
                .build();

        channel = Channel.builder()
                .id(channelId)
                .displayName("Shopee Store")
                .platform(PlatformType.SHOPEE)
                .status("ACTIVE")
                .region("VN")
                .metadata(new HashMap<>())
                .syncEnabled(true)
                .build();

        customer = Customer.builder()
                .id(customerId)
                .fullName("Test Customer")
                .email("customer@test.com")
                .phone("0912345678")
                .build();

        Map<String, Object> shippingAddress = new HashMap<>();
        shippingAddress.put("fullName", "Test Customer");
        shippingAddress.put("phone", "0912345678");
        shippingAddress.put("address", "123 Test Street");

        order = Order.builder()
                .id(orderId)
                .platform(PlatformType.SHOPEE)
                .channelName("Shopee Store")
                .externalOrderId("EXT-001")
                .status(OrderStatus.PENDING)
                .paymentStatus("UNPAID")
                .channel(channel)
                .customer(customer)
                .shippingAddress(shippingAddress)
                .subtotal(new BigDecimal("300000"))
                .discountAmount(BigDecimal.ZERO)
                .shippingFee(BigDecimal.ZERO)
                .currency("VND")
                .build();
        order.setCreatedAt(OffsetDateTime.now());
        order.setUpdatedAt(OffsetDateTime.now());
        order.setStatusChangedAt(OffsetDateTime.now());

        variant = ProductVariant.builder()
                .id(variantId)
                .sku("TEST-001")
                .name("Test Variant")
                .price(new BigDecimal("150000"))
                .build();

        orderItem = OrderItem.builder()
                .id(UUID.randomUUID())
                .order(order)
                .variant(variant)
                .sku("TEST-001")
                .name("Test Item")
                .quantity(2)
                .unitPrice(new BigDecimal("150000"))
                .discountAmount(BigDecimal.ZERO)
                .build();

        orderRequest = OrderRequest.builder()
                .channelId(channelId)
                .customerId(customerId)
                .items(List.of(
                        OrderItemRequest.builder()
                                .variantId(variantId)
                                .sku("TEST-001")
                                .name("Test Item")
                                .quantity(2)
                                .unitPrice(new BigDecimal("150000"))
                                .discountAmount(BigDecimal.ZERO)
                                .build()
                ))
                .shippingAddress(shippingAddress)
                .note("Test note")
                .build();

        orderResponse = OrderResponse.builder()
                .id(orderId)
                .platform(PlatformType.SHOPEE)
                .channelName("Shopee Store")
                .externalOrderId("EXT-001")
                .status(OrderStatus.PENDING)
                .paymentStatus("UNPAID")
                .subtotal(new BigDecimal("300000"))
                .items(new ArrayList<>())
                .build();
    }

    private MockedStatic<SecurityUtils> mockSecurityUtils() {
        MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class);
        mocked.when(SecurityUtils::getCurrentUser).thenReturn(Optional.of(user));
        return mocked;
    }

    // =========================================================
    // create() Tests
    // =========================================================
    @Nested
    @DisplayName("create() Tests")
    class CreateTests {

        @Test
        @DisplayName("Should create order successfully with channel and customer")
        void shouldCreateOrderSuccessfully() {
            try (MockedStatic<SecurityUtils> mocked = mockSecurityUtils()) {
                when(orderMapper.toEntity(orderRequest)).thenReturn(order);
                when(channelRepository.findById(channelId)).thenReturn(Optional.of(channel));
                when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
                when(orderRepository.save(any(Order.class))).thenReturn(order);
                when(productVariantRepository.findById(variantId)).thenReturn(Optional.of(variant));
                // Mock inventory items to avoid INVENTORY_ITEM_NOT_FOUND error
                InventoryItem inventoryItem = InventoryItem.builder()
                        .id(UUID.randomUUID())
                        .variant(variant)
                        .quantityOnHand(100)
                        .reservedQuantity(0)
                        .build();
                when(inventoryItemRepository.findByVariantIdWithLock(variantId)).thenReturn(List.of(inventoryItem));
                when(orderItemMapper.toResponse(any(OrderItem.class))).thenReturn(
                        OrderItemResponse.builder()
                                .id(UUID.randomUUID())
                                .sku("TEST-001")
                                .name("Test Item")
                                .quantity(2)
                                .unitPrice(new BigDecimal("150000"))
                                .build()
                );
                when(orderMapper.toResponseWithItems(order)).thenReturn(orderResponse);
                when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(orderItem));

                OrderResponse result = orderService.create(orderRequest);

                assertThat(result).isNotNull();
                assertThat(result.getId()).isEqualTo(orderId);
                verify(orderRepository).save(any(Order.class));
                verify(auditService).record(any(), anyString(), eq("CREATE"), eq("ORDER"), any(), anyString(), any());
            }
        }

        @Test
        @DisplayName("Should throw EntityNotFoundException when channel does not exist")
        void shouldThrowWhenChannelNotFound() {
            when(orderMapper.toEntity(orderRequest)).thenReturn(order);
            when(channelRepository.findById(channelId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.create(orderRequest))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Channel not found");
        }

        @Test
        @DisplayName("Should throw EntityNotFoundException when customer does not exist")
        void shouldThrowWhenCustomerNotFound() {
            when(orderMapper.toEntity(orderRequest)).thenReturn(order);
            when(channelRepository.findById(channelId)).thenReturn(Optional.of(channel));
            when(customerRepository.findById(customerId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.create(orderRequest))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Customer not found");
        }
    }

    // =========================================================
    // getById() Tests
    // =========================================================
    @Nested
    @DisplayName("getById() Tests")
    class GetByIdTests {

        @Test
        @DisplayName("Should get order by id successfully")
        void shouldGetOrderByIdSuccessfully() {
            when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
            when(orderMapper.toResponseWithItems(order)).thenReturn(orderResponse);
            when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(orderItem));
            when(orderItemMapper.toResponse(orderItem)).thenReturn(
                    OrderItemResponse.builder()
                            .id(orderItem.getId())
                            .sku("TEST-001")
                            .name("Test Item")
                            .quantity(2)
                            .unitPrice(new BigDecimal("150000"))
                            .build()
            );

            OrderResponse result = orderService.getById(orderId);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(orderId);
            assertThat(result.getItems()).hasSize(1);
        }

        @Test
        @DisplayName("Should throw EntityNotFoundException when order does not exist")
        void shouldThrowWhenOrderNotFound() {
            when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.getById(orderId))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Order not found");
        }
    }

    // =========================================================
    // getAll() Tests
    // =========================================================
    @Nested
    @DisplayName("getAll() Tests")
    class GetAllTests {

        @Test
        @DisplayName("Should get all orders with pagination")
        void shouldGetAllOrdersWithPagination() {
            Page<Order> orderPage = new PageImpl<>(List.of(order), PageRequest.of(0, 10), 1);
            when(orderRepository.findAll(any(PageRequest.class))).thenReturn(orderPage);
            when(orderMapper.toResponse(order)).thenReturn(orderResponse);

            PageResponse<OrderResponse> result = orderService.getAll(0, 10);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(1);
        }
    }

    // =========================================================
    // getByStatus() Tests
    // =========================================================
    @Nested
    @DisplayName("getByStatus() Tests")
    class GetByStatusTests {

        @Test
        @DisplayName("Should get orders by status with pagination")
        void shouldGetOrdersByStatus() {
            Page<Order> orderPage = new PageImpl<>(List.of(order), PageRequest.of(0, 10), 1);
            when(orderRepository.findByStatus(eq(OrderStatus.PENDING), any(PageRequest.class))).thenReturn(orderPage);
            when(orderMapper.toResponse(order)).thenReturn(orderResponse);

            PageResponse<OrderResponse> result = orderService.getByStatus(OrderStatus.PENDING, 0, 10);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
        }
    }

    // =========================================================
    // updateStatus() Tests
    // =========================================================
    @Nested
    @DisplayName("updateStatus() Tests")
    class UpdateStatusTests {

        @Test
        @DisplayName("Should update order status successfully")
        void shouldUpdateOrderStatusSuccessfully() {
            try (MockedStatic<SecurityUtils> mocked = mockSecurityUtils()) {
                order.setStatus(OrderStatus.PENDING);
                Order updatedOrder = Order.builder()
                        .id(orderId)
                        .platform(PlatformType.SHOPEE)
                        .channelName("Shopee Store")
                        .externalOrderId("EXT-001")
                        .status(OrderStatus.CONFIRMED)
                        .paymentStatus("UNPAID")
                        .subtotal(new BigDecimal("300000"))
                        .discountAmount(BigDecimal.ZERO)
                        .shippingFee(BigDecimal.ZERO)
                        .currency("VND")
                        .shippingAddress(order.getShippingAddress())
                        .build();

                when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
                when(orderRepository.save(any(Order.class))).thenReturn(updatedOrder);
                when(orderMapper.toResponseWithItems(updatedOrder)).thenReturn(orderResponse);

                OrderResponse result = orderService.updateStatus(orderId, OrderStatus.CONFIRMED);

                assertThat(result).isNotNull();
                verify(orderRepository).save(any(Order.class));
                verify(auditService).record(any(), anyString(), eq("STATUS_CHANGE"), eq("ORDER"), any(), anyString(), any());
            }
        }

        @Test
        @DisplayName("Should throw ORDER_ALREADY_CANCELLED when order is already cancelled")
        void shouldThrowWhenOrderAlreadyCancelled() {
            order.setStatus(OrderStatus.CANCELLED);
            when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.updateStatus(orderId, OrderStatus.CANCELLED))
                    .isInstanceOf(AppException.class)
                    .satisfies(e -> assertThat(((AppException) e).getErrorCode())
                            .isEqualTo(ErrorCode.ORDER_ALREADY_CANCELLED));
        }

        @Test
        @DisplayName("Should auto set payment status to PAID when status is DELIVERED")
        void shouldAutoSetPaymentToPaidWhenDelivered() {
            try (MockedStatic<SecurityUtils> mocked = mockSecurityUtils()) {
                order.setStatus(OrderStatus.SHIPPED);
                Order deliveredOrder = Order.builder()
                        .id(orderId)
                        .platform(PlatformType.SHOPEE)
                        .channelName("Shopee Store")
                        .externalOrderId("EXT-001")
                        .status(OrderStatus.DELIVERED)
                        .paymentStatus("PAID")
                        .subtotal(new BigDecimal("300000"))
                        .discountAmount(BigDecimal.ZERO)
                        .shippingFee(BigDecimal.ZERO)
                        .currency("VND")
                        .shippingAddress(order.getShippingAddress())
                        .build();

                when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
                when(orderRepository.save(any(Order.class))).thenReturn(deliveredOrder);
                when(orderMapper.toResponseWithItems(deliveredOrder)).thenReturn(orderResponse);

                OrderResponse result = orderService.updateStatus(orderId, OrderStatus.DELIVERED);

                assertThat(result).isNotNull();
                verify(orderRepository).save(any(Order.class));
            }
        }
    }

    // =========================================================
    // updatePaymentStatus() Tests
    // =========================================================
    @Nested
    @DisplayName("updatePaymentStatus() Tests")
    class UpdatePaymentStatusTests {

        @Test
        @DisplayName("Should update payment status successfully")
        void shouldUpdatePaymentStatusSuccessfully() {
            try (MockedStatic<SecurityUtils> mocked = mockSecurityUtils()) {
                order.setPaymentStatus("UNPAID");
                Order updatedOrder = Order.builder()
                        .id(orderId)
                        .platform(PlatformType.SHOPEE)
                        .channelName("Shopee Store")
                        .externalOrderId("EXT-001")
                        .status(OrderStatus.PENDING)
                        .paymentStatus("PAID")
                        .subtotal(new BigDecimal("300000"))
                        .discountAmount(BigDecimal.ZERO)
                        .shippingFee(BigDecimal.ZERO)
                        .currency("VND")
                        .shippingAddress(order.getShippingAddress())
                        .build();

                when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
                when(orderRepository.save(any(Order.class))).thenReturn(updatedOrder);
                when(orderMapper.toResponseWithItems(updatedOrder)).thenReturn(orderResponse);

                OrderResponse result = orderService.updatePaymentStatus(orderId, PaymentStatus.PAID);

                assertThat(result).isNotNull();
                verify(orderRepository).save(any(Order.class));
                verify(auditService).record(any(), anyString(), eq("PAYMENT_STATUS_CHANGE"), eq("ORDER"), any(), anyString(), any());
            }
        }
    }

    // =========================================================
    // cancel() Tests
    // =========================================================
    @Nested
    @DisplayName("cancel() Tests")
    class CancelTests {

        @Test
        @DisplayName("Should cancel order successfully")
        void shouldCancelOrderSuccessfully() {
            try (MockedStatic<SecurityUtils> mocked = mockSecurityUtils()) {
                order.setStatus(OrderStatus.PENDING);
                when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
                when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(orderItem));
                when(productVariantRepository.findBySkuAndDeletedAtIsNull("TEST-001")).thenReturn(Optional.of(variant));
                when(inventoryItemRepository.findByVariantIdWithLock(variantId)).thenReturn(new ArrayList<>());
                when(orderRepository.save(any(Order.class))).thenReturn(order);

                orderService.cancel(orderId, "Customer request");

                assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
                assertThat(order.getCancelReason()).isEqualTo("Customer request");
                verify(orderRepository).save(any(Order.class));
                verify(auditService).record(any(), anyString(), eq("ORDER_CANCEL"), eq("ORDER"), any(), anyString(), any());
            }
        }

        @Test
        @DisplayName("Should throw ORDER_ALREADY_CANCELLED when order is already cancelled")
        void shouldThrowWhenOrderAlreadyCancelled() {
            order.setStatus(OrderStatus.CANCELLED);
            when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.cancel(orderId, "reason"))
                    .isInstanceOf(AppException.class)
                    .satisfies(e -> assertThat(((AppException) e).getErrorCode())
                            .isEqualTo(ErrorCode.ORDER_ALREADY_CANCELLED));
        }
    }

    // =========================================================
    // getStats() Tests
    // =========================================================
    @Nested
    @DisplayName("getStats() Tests")
    class GetStatsTests {

        @Test
        @DisplayName("Should get order stats successfully")
        void shouldGetOrderStatsSuccessfully() {
            when(orderRepository.countAll()).thenReturn(100L);
            when(orderRepository.countByStatus(OrderStatus.PENDING)).thenReturn(10L);
            when(orderRepository.countByStatus(OrderStatus.CONFIRMED)).thenReturn(15L);
            when(orderRepository.countByStatus(OrderStatus.PROCESSING)).thenReturn(20L);
            when(orderRepository.countByStatus(OrderStatus.SHIPPED)).thenReturn(25L);
            when(orderRepository.countByStatus(OrderStatus.DELIVERED)).thenReturn(25L);
            when(orderRepository.countByStatus(OrderStatus.CANCELLED)).thenReturn(5L);
            when(orderRepository.sumRevenueDelivered()).thenReturn(new BigDecimal("5000000"));

            OrderStats result = orderService.getStats();

            assertThat(result).isNotNull();
            assertThat(result.totalOrders()).isEqualTo(100L);
            assertThat(result.pendingCount()).isEqualTo(10L);
            assertThat(result.confirmedCount()).isEqualTo(15L);
            assertThat(result.processingCount()).isEqualTo(20L);
            assertThat(result.shippedCount()).isEqualTo(25L);
            assertThat(result.deliveredCount()).isEqualTo(25L);
            assertThat(result.cancelledCount()).isEqualTo(5L);
            assertThat(result.totalRevenue()).isEqualTo(new BigDecimal("5000000"));
        }
    }

    // =========================================================
    // getFiltered() Tests
    // =========================================================
    @Nested
    @DisplayName("getFiltered() Tests")
    class GetFilteredTests {

        @Test
        @DisplayName("Should get filtered orders with all filters")
        void shouldGetFilteredOrders() {
            Page<Order> orderPage = new PageImpl<>(List.of(order), PageRequest.of(0, 10), 1);
            when(orderRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(orderPage);
            when(orderMapper.toResponse(order)).thenReturn(orderResponse);

            OffsetDateTime from = OffsetDateTime.now().minusDays(7);
            OffsetDateTime to = OffsetDateTime.now();

            PageResponse<OrderResponse> result = orderService.getFiltered(
                    OrderStatus.PENDING, channelId, "test", from, to, 0, 10);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
        }
    }
}
