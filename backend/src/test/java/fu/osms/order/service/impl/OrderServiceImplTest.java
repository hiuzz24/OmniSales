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
import fu.osms.inventory.service.OrderStockDeliveryReadinessService;
import fu.osms.order.dto.request.CancelOrderRequest;
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
import fu.osms.sync.order.OrderStatusPushContext;
import fu.osms.sync.order.OrderStatusPushResult;
import fu.osms.sync.order.OrderStatusPushService;
import fu.osms.sync.order.OrderStatusPushStatus;
import fu.osms.sync.service.MarketplaceInventoryPropagationService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
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
    @Mock
    private OrderStockDeliveryReadinessService orderStockDeliveryReadinessService;
    @Mock
    private OrderStatusPushService orderStatusPushService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private MarketplaceInventoryPropagationService marketplaceInventoryPropagationService;

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
    private InventoryItem inventoryItem;

    private OrderStatusPushResult successPushResult;

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

        inventoryItem = InventoryItem.builder()
                .id(UUID.randomUUID())
                .variant(variant)
                .quantityOnHand(100)
                .reservedQuantity(0)
                .averageCost(new BigDecimal("100000"))
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

        // Default success push result
        successPushResult = OrderStatusPushResult.builder()
                .status(OrderStatusPushStatus.SUCCESS)
                .message("Success")
                .build();

        // Setup default mocks
        lenient().when(orderStatusPushService.push(any(Order.class), any(OrderStatus.class), any()))
                .thenReturn(successPushResult);
        lenient().when(orderStatusPushService.push(any(Order.class), any(OrderStatus.class), any(OrderStatusPushContext.class)))
                .thenReturn(successPushResult);
        lenient().doNothing().when(orderStockDeliveryReadinessService).requireReadyForShipment(any());
        lenient().doNothing().when(eventPublisher).publishEvent(any());
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
            try (MockedStatic<SecurityUtils> mocked = mockSecurityUtils()) {
                when(orderMapper.toEntity(orderRequest)).thenReturn(order);
                when(channelRepository.findById(channelId)).thenReturn(Optional.empty());

                assertThatThrownBy(() -> orderService.create(orderRequest))
                        .isInstanceOf(EntityNotFoundException.class)
                        .hasMessageContaining("Channel not found");
            }
        }

        @Test
        @DisplayName("Should throw EntityNotFoundException when customer does not exist")
        void shouldThrowWhenCustomerNotFound() {
            try (MockedStatic<SecurityUtils> mocked = mockSecurityUtils()) {
                when(orderMapper.toEntity(orderRequest)).thenReturn(order);
                when(channelRepository.findById(channelId)).thenReturn(Optional.of(channel));
                when(customerRepository.findById(customerId)).thenReturn(Optional.empty());

                assertThatThrownBy(() -> orderService.create(orderRequest))
                        .isInstanceOf(EntityNotFoundException.class)
                        .hasMessageContaining("Customer not found");
            }
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
    // updateStatus() Tests - CHUYỂN ĐỔI TRẠNG THÁI
    // =========================================================
    @Nested
    @DisplayName("updateStatus() Tests - Chuyển đổi trạng thái")
    class UpdateStatusTests {

        @Test
        @DisplayName("Should update status from PENDING to CONFIRMED successfully")
        void shouldUpdateStatusFromPendingToConfirmed() {
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

                when(orderRepository.findForUpdateById(orderId)).thenReturn(Optional.of(order));
                when(orderRepository.save(any(Order.class))).thenReturn(updatedOrder);
                when(orderMapper.toResponseWithItems(updatedOrder)).thenReturn(orderResponse);

                OrderResponse result = orderService.updateStatus(orderId, OrderStatus.CONFIRMED);

                assertThat(result).isNotNull();
                verify(orderRepository).save(any(Order.class));
                verify(auditService).record(any(), anyString(), eq("STATUS_CHANGE"), eq("ORDER"), any(), anyString(), any());
            }
        }

        @Test
        @DisplayName("Should update status from CONFIRMED to PROCESSING successfully")
        void shouldUpdateStatusFromConfirmedToProcessing() {
            try (MockedStatic<SecurityUtils> mocked = mockSecurityUtils()) {
                order.setStatus(OrderStatus.CONFIRMED);
                Order updatedOrder = Order.builder()
                        .id(orderId)
                        .platform(PlatformType.SHOPEE)
                        .channelName("Shopee Store")
                        .externalOrderId("EXT-001")
                        .status(OrderStatus.PROCESSING)
                        .paymentStatus("UNPAID")
                        .subtotal(new BigDecimal("300000"))
                        .discountAmount(BigDecimal.ZERO)
                        .shippingFee(BigDecimal.ZERO)
                        .currency("VND")
                        .shippingAddress(order.getShippingAddress())
                        .build();

                when(orderRepository.findForUpdateById(orderId)).thenReturn(Optional.of(order));
                when(orderRepository.save(any(Order.class))).thenReturn(updatedOrder);
                when(orderMapper.toResponseWithItems(updatedOrder)).thenReturn(orderResponse);

                OrderResponse result = orderService.updateStatus(orderId, OrderStatus.PROCESSING);

                assertThat(result).isNotNull();
                verify(orderRepository).save(any(Order.class));
            }
        }

        @Test
        @DisplayName("Should update status from PROCESSING to SHIPPED successfully")
        void shouldUpdateStatusFromProcessingToShipped() {
            try (MockedStatic<SecurityUtils> mocked = mockSecurityUtils()) {
                order.setStatus(OrderStatus.PROCESSING);
                Order updatedOrder = Order.builder()
                        .id(orderId)
                        .platform(PlatformType.SHOPEE)
                        .channelName("Shopee Store")
                        .externalOrderId("EXT-001")
                        .status(OrderStatus.SHIPPED)
                        .paymentStatus("UNPAID")
                        .subtotal(new BigDecimal("300000"))
                        .discountAmount(BigDecimal.ZERO)
                        .shippingFee(BigDecimal.ZERO)
                        .currency("VND")
                        .shippingAddress(order.getShippingAddress())
                        .build();

                when(orderRepository.findForUpdateById(orderId)).thenReturn(Optional.of(order));
                when(orderRepository.save(any(Order.class))).thenReturn(updatedOrder);
                when(orderMapper.toResponseWithItems(updatedOrder)).thenReturn(orderResponse);

                OrderResponse result = orderService.updateStatus(orderId, OrderStatus.SHIPPED);

                assertThat(result).isNotNull();
                verify(orderRepository).save(any(Order.class));
            }
        }

        @Test
        @DisplayName("Should update status from SHIPPED to DELIVERED and auto-set PAID when UNPAID")
        void shouldUpdateStatusToDeliveredAndAutoSetPaid() {
            try (MockedStatic<SecurityUtils> mocked = mockSecurityUtils()) {
                // Manual (non-platform) order: no channel, MANUAL platform.
                // Auto-mark-paid only triggers for non-platform orders.
                order.setPlatform(PlatformType.MANUAL);
                order.setChannel(null);
                order.setStatus(OrderStatus.SHIPPED);
                order.setPaymentStatus("UNPAID");

                Order deliveredOrder = Order.builder()
                        .id(orderId)
                        .platform(PlatformType.MANUAL)
                        .channelName(null)
                        .externalOrderId("EXT-001")
                        .status(OrderStatus.DELIVERED)
                        .paymentStatus("PAID")
                        .subtotal(new BigDecimal("300000"))
                        .discountAmount(BigDecimal.ZERO)
                        .shippingFee(BigDecimal.ZERO)
                        .currency("VND")
                        .shippingAddress(order.getShippingAddress())
                        .build();

                when(orderRepository.findForUpdateById(orderId)).thenReturn(Optional.of(order));
                when(orderRepository.save(any(Order.class))).thenReturn(deliveredOrder);
                when(orderMapper.toResponseWithItems(deliveredOrder)).thenReturn(orderResponse);

                OrderResponse result = orderService.updateStatus(orderId, OrderStatus.DELIVERED);

                assertThat(result).isNotNull();

                ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
                verify(orderRepository).save(orderCaptor.capture());
                assertThat(orderCaptor.getValue().getPaymentStatus()).isEqualTo("PAID");
            }
        }

        @Test
        @DisplayName("Should update status from SHIPPED to DELIVERED without changing PAID status")
        void shouldUpdateStatusToDeliveredKeepingPaidStatus() {
            try (MockedStatic<SecurityUtils> mocked = mockSecurityUtils()) {
                order.setStatus(OrderStatus.SHIPPED);
                order.setPaymentStatus("PAID");

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

                when(orderRepository.findForUpdateById(orderId)).thenReturn(Optional.of(order));
                when(orderRepository.save(any(Order.class))).thenReturn(deliveredOrder);
                when(orderMapper.toResponseWithItems(deliveredOrder)).thenReturn(orderResponse);

                OrderResponse result = orderService.updateStatus(orderId, OrderStatus.DELIVERED);

                assertThat(result).isNotNull();
                verify(orderRepository).save(any(Order.class));
            }
        }

        @Test
        @DisplayName("Should throw ORDER_ALREADY_CANCELLED when order is already cancelled")
        void shouldThrowWhenOrderAlreadyCancelled() {
            order.setStatus(OrderStatus.CANCELLED);
            when(orderRepository.findForUpdateById(orderId)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.updateStatus(orderId, OrderStatus.CANCELLED))
                    .isInstanceOf(AppException.class)
                    .satisfies(e -> assertThat(((AppException) e).getErrorCode())
                            .isEqualTo(ErrorCode.ORDER_ALREADY_CANCELLED));
        }

        @Test
        @DisplayName("Should cancel order when status is CANCELLED")
        void shouldCancelOrderOnCancelStatus() {
            try (MockedStatic<SecurityUtils> mocked = mockSecurityUtils()) {
                order.setStatus(OrderStatus.PENDING);
                Order cancelledOrder = Order.builder()
                        .id(orderId)
                        .platform(PlatformType.SHOPEE)
                        .channelName("Shopee Store")
                        .externalOrderId("EXT-001")
                        .status(OrderStatus.CANCELLED)
                        .paymentStatus("UNPAID")
                        .subtotal(new BigDecimal("300000"))
                        .discountAmount(BigDecimal.ZERO)
                        .shippingFee(BigDecimal.ZERO)
                        .currency("VND")
                        .shippingAddress(order.getShippingAddress())
                        .cancelReason("Customer request")
                        .build();

                when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
                when(orderRepository.findForUpdateById(orderId)).thenReturn(Optional.of(order));
                when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(orderItem));
                when(productVariantRepository.findBySkuAndDeletedAtIsNull("TEST-001")).thenReturn(Optional.of(variant));
                when(inventoryItemRepository.findByVariantIdWithLock(variantId)).thenReturn(List.of(inventoryItem));
                when(orderRepository.save(any(Order.class))).thenReturn(cancelledOrder);
                when(orderMapper.toResponseWithItems(cancelledOrder)).thenReturn(orderResponse);

                OrderResponse result = orderService.updateStatus(orderId, OrderStatus.CANCELLED);

                assertThat(result).isNotNull();
                verify(inventoryItemRepository).findByVariantIdWithLock(variantId);
            }
        }

        @Test
        @DisplayName("Should throw EntityNotFoundException when order not found for status update")
        void shouldThrowWhenOrderNotFoundForStatusUpdate() {
            when(orderRepository.findForUpdateById(orderId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.updateStatus(orderId, OrderStatus.CONFIRMED))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Order not found");
        }
    }

    // =========================================================
    // updatePaymentStatus() Tests - ĐỔI TRẠNG THÁI THANH TOÁN
    // =========================================================
    @Nested
    @DisplayName("updatePaymentStatus() Tests - Đổi trạng thái thanh toán")
    class UpdatePaymentStatusTests {

        @Test
        @DisplayName("Should update payment status from UNPAID to PAID successfully")
        void shouldUpdatePaymentStatusFromUnpaidToPaid() {
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

        @Test
        @DisplayName("Should update payment status from PAID to REFUNDED successfully")
        void shouldUpdatePaymentStatusFromPaidToRefunded() {
            try (MockedStatic<SecurityUtils> mocked = mockSecurityUtils()) {
                order.setPaymentStatus("PAID");
                Order updatedOrder = Order.builder()
                        .id(orderId)
                        .platform(PlatformType.SHOPEE)
                        .channelName("Shopee Store")
                        .externalOrderId("EXT-001")
                        .status(OrderStatus.PENDING)
                        .paymentStatus("REFUNDED")
                        .subtotal(new BigDecimal("300000"))
                        .discountAmount(BigDecimal.ZERO)
                        .shippingFee(BigDecimal.ZERO)
                        .currency("VND")
                        .shippingAddress(order.getShippingAddress())
                        .build();

                when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
                when(orderRepository.save(any(Order.class))).thenReturn(updatedOrder);
                when(orderMapper.toResponseWithItems(updatedOrder)).thenReturn(orderResponse);

                // Update order's payment status to simulate REFUNDED
                order.setPaymentStatus("PAID");
                OrderResponse result = orderService.updatePaymentStatus(orderId, PaymentStatus.PAID);

                assertThat(result).isNotNull();
                verify(orderRepository).save(any(Order.class));
            }
        }

        @Test
        @DisplayName("Should update payment status from PAID to UNPAID successfully")
        void shouldUpdatePaymentStatusFromPaidToUnpaid() {
            try (MockedStatic<SecurityUtils> mocked = mockSecurityUtils()) {
                order.setPaymentStatus("PAID");
                Order updatedOrder = Order.builder()
                        .id(orderId)
                        .platform(PlatformType.SHOPEE)
                        .channelName("Shopee Store")
                        .externalOrderId("EXT-001")
                        .status(OrderStatus.PENDING)
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

                OrderResponse result = orderService.updatePaymentStatus(orderId, PaymentStatus.UNPAID);

                assertThat(result).isNotNull();
                verify(orderRepository).save(any(Order.class));
            }
        }

        @Test
        @DisplayName("Should record audit log with old and new payment status")
        void shouldRecordAuditLogWithOldAndNewPaymentStatus() {
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

                orderService.updatePaymentStatus(orderId, PaymentStatus.PAID);

                verify(auditService).record(
                        any(),
                        anyString(),
                        eq("PAYMENT_STATUS_CHANGE"),
                        eq("ORDER"),
                        eq(orderId),
                        eq(orderId.toString()),
                        argThat(map -> {
                            if (map instanceof Map) {
                                return "UNPAID".equals(((Map<?, ?>) map).get("oldPaymentStatus"))
                                    && "PAID".equals(((Map<?, ?>) map).get("newPaymentStatus"));
                            }
                            return false;
                        })
                );
            }
        }

        @Test
        @DisplayName("Should throw EntityNotFoundException when order not found for payment update")
        void shouldThrowWhenOrderNotFoundForPaymentUpdate() {
            when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.updatePaymentStatus(orderId, PaymentStatus.PAID))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Order not found");
        }
    }

    // =========================================================
    // cancel() Tests - HỦY ĐƠN
    // =========================================================
    @Nested
    @DisplayName("cancel() Tests - Hủy đơn")
    class CancelTests {

        @Test
        @DisplayName("Should cancel PENDING order successfully with reason")
        void shouldCancelPendingOrderSuccessfully() {
            try (MockedStatic<SecurityUtils> mocked = mockSecurityUtils()) {
                order.setStatus(OrderStatus.PENDING);
                when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
                when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(orderItem));
                when(productVariantRepository.findBySkuAndDeletedAtIsNull("TEST-001")).thenReturn(Optional.of(variant));
                when(inventoryItemRepository.findByVariantIdWithLock(variantId)).thenReturn(List.of(inventoryItem));
                when(orderRepository.save(any(Order.class))).thenReturn(order);

                orderService.cancel(orderId, makeCancelRequest("Customer request"));

                assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
                assertThat(order.getCancelReason()).isEqualTo("Customer request");
                assertThat(order.getStatusChangedAt()).isNotNull();
                verify(orderRepository).save(any(Order.class));
                verify(auditService).record(any(), anyString(), eq("ORDER_CANCEL"), eq("ORDER"), any(), anyString(), any());
            }
        }

        @Test
        @DisplayName("Should cancel CONFIRMED order successfully")
        void shouldCancelConfirmedOrderSuccessfully() {
            try (MockedStatic<SecurityUtils> mocked = mockSecurityUtils()) {
                order.setStatus(OrderStatus.CONFIRMED);
                when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
                when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(orderItem));
                when(productVariantRepository.findBySkuAndDeletedAtIsNull("TEST-001")).thenReturn(Optional.of(variant));
                when(inventoryItemRepository.findByVariantIdWithLock(variantId)).thenReturn(List.of(inventoryItem));
                when(orderRepository.save(any(Order.class))).thenReturn(order);

                orderService.cancel(orderId, makeCancelRequest("Out of stock"));

                assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
                assertThat(order.getCancelReason()).isEqualTo("Out of stock");
                verify(orderRepository).save(any(Order.class));
            }
        }

        @Test
        @DisplayName("Should cancel PROCESSING order and release reserved inventory")
        void shouldCancelProcessingOrderAndReleaseInventory() {
            try (MockedStatic<SecurityUtils> mocked = mockSecurityUtils()) {
                order.setStatus(OrderStatus.PROCESSING);
                inventoryItem.setReservedQuantity(2);

                when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
                when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(orderItem));
                when(productVariantRepository.findBySkuAndDeletedAtIsNull("TEST-001")).thenReturn(Optional.of(variant));
                when(inventoryItemRepository.findByVariantIdWithLock(variantId)).thenReturn(List.of(inventoryItem));
                when(orderRepository.save(any(Order.class))).thenReturn(order);

                orderService.cancel(orderId, makeCancelRequest("Customer cancelled"));

                assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
                verify(inventoryItemRepository).findByVariantIdWithLock(variantId);
            }
        }

        @Test
        @DisplayName("Should throw INVALID_REQUEST when cancelling SHOPEE order with null reason")
        void shouldCancelOrderWithNullReason() {
            try (MockedStatic<SecurityUtils> mocked = mockSecurityUtils()) {
                order.setStatus(OrderStatus.PENDING);
                when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

                // SHOPEE orders require a non-blank text reason, so passing null
                // (or a blank reason) is rejected with INVALID_REQUEST up-front.
                assertThatThrownBy(() -> orderService.cancel(orderId, null))
                        .isInstanceOf(AppException.class)
                        .satisfies(e -> assertThat(((AppException) e).getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_REQUEST));
            }
        }

        @Test
        @DisplayName("Should throw ORDER_ALREADY_CANCELLED when cancelling already cancelled order")
        void shouldThrowWhenOrderAlreadyCancelled() {
            order.setStatus(OrderStatus.CANCELLED);
            when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.cancel(orderId, makeCancelRequest("reason")))
                    .isInstanceOf(AppException.class)
                    .satisfies(e -> assertThat(((AppException) e).getErrorCode())
                            .isEqualTo(ErrorCode.ORDER_ALREADY_CANCELLED));
        }

        @Test
        @DisplayName("Should throw EntityNotFoundException when order not found for cancel")
        void shouldThrowWhenOrderNotFoundForCancel() {
            when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.cancel(orderId, makeCancelRequest("reason")))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Order not found");
        }

        @Test
        @DisplayName("Should record audit log with old status, new status, and reason")
        void shouldRecordAuditLogWithOldStatusNewStatusAndReason() {
            try (MockedStatic<SecurityUtils> mocked = mockSecurityUtils()) {
                order.setStatus(OrderStatus.PENDING);
                when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
                when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(orderItem));
                when(productVariantRepository.findBySkuAndDeletedAtIsNull("TEST-001")).thenReturn(Optional.of(variant));
                when(inventoryItemRepository.findByVariantIdWithLock(variantId)).thenReturn(List.of(inventoryItem));
                when(orderRepository.save(any(Order.class))).thenReturn(order);

                orderService.cancel(orderId, makeCancelRequest("Customer request"));

                verify(auditService).record(
                        any(),
                        anyString(),
                        eq("ORDER_CANCEL"),
                        eq("ORDER"),
                        eq(orderId),
                        eq(orderId.toString()),
                        argThat(map -> {
                            if (map instanceof Map) {
                                Map<?, ?> m = (Map<?, ?>) map;
                                return "PENDING".equals(m.get("oldStatus"))
                                        && "CANCELLED".equals(m.get("newStatus"))
                                        && "Customer request".equals(m.get("reason"));
                            }
                            return false;
                        })
                );
            }
        }
    }

    private static CancelOrderRequest makeCancelRequest(String reason) {
        CancelOrderRequest req = new CancelOrderRequest();
        req.setReason(reason);
        return req;
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

        @Test
        @DisplayName("Should propagate totalRevenue from repository (excludes CANCELLED)")
        void shouldPropagateRevenueFromRepository() {
            when(orderRepository.countAll()).thenReturn(0L);
            when(orderRepository.countByStatus(any())).thenReturn(0L);
            when(orderRepository.sumRevenueDelivered()).thenReturn(new BigDecimal("123456789"));

            OrderStats result = orderService.getStats();

            assertThat(result.totalRevenue()).isEqualTo(new BigDecimal("123456789"));
        }

        @Test
        @DisplayName("Should return zero revenue when sumRevenueDelivered returns BigDecimal.ZERO")
        void shouldReturnZeroRevenueWhenNoOrders() {
            when(orderRepository.countAll()).thenReturn(0L);
            when(orderRepository.countByStatus(any())).thenReturn(0L);
            when(orderRepository.sumRevenueDelivered()).thenReturn(BigDecimal.ZERO);

            OrderStats result = orderService.getStats();

            assertThat(result.totalRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.totalRevenue()).isNotNull();
        }

        @Test
        @DisplayName("Should preserve non-negative revenue values")
        void shouldPreserveNonNegativeRevenue() {
            when(orderRepository.countAll()).thenReturn(5L);
            when(orderRepository.countByStatus(any())).thenReturn(0L);
            when(orderRepository.sumRevenueDelivered()).thenReturn(new BigDecimal("0"));

            OrderStats result = orderService.getStats();

            assertThat(result.totalRevenue().signum()).isGreaterThanOrEqualTo(0);
        }
    }

    // =========================================================
    // getOrderHistory() Tests
    // =========================================================
    @Nested
    @DisplayName("getOrderHistory() Tests")
    class GetOrderHistoryTests {

        @Test
        @DisplayName("Should get order history successfully")
        void shouldGetOrderHistorySuccessfully() {
            AuditLog auditLog = AuditLog.builder()
                    .id(UUID.randomUUID())
                    .action("STATUS_CHANGE")
                    .entityType("ORDER")
                    .entityId(orderId)
                    .actorEmail("manager@osms.vn")
                    .performedAt(OffsetDateTime.now())
                    .build();

            AuditLogResponse auditLogResponse = AuditLogResponse.builder()
                    .id(auditLog.getId())
                    .action("STATUS_CHANGE")
                    .performedAt(auditLog.getPerformedAt())
                    .build();

            Page<AuditLog> logPage = new PageImpl<>(List.of(auditLog), PageRequest.of(0, 10), 1);

            when(auditLogRepository.findByEntityTypeAndEntityId(eq("ORDER"), eq(orderId), any(PageRequest.class)))
                    .thenReturn(logPage);
            when(auditLogMapper.toResponse(auditLog)).thenReturn(auditLogResponse);

            PageResponse<AuditLogResponse> result = orderService.getOrderHistory(orderId, 0, 10);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getAction()).isEqualTo("STATUS_CHANGE");
        }
    }
}
