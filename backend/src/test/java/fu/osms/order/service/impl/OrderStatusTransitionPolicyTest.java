package fu.osms.order.service.impl;

import fu.osms.channel.entity.Channel;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.exception.AppException;
import fu.osms.inventory.service.OrderStockDeliveryReadinessService;
import fu.osms.order.entity.Order;
import fu.osms.order.enums.OrderStatus;
import fu.osms.sync.order.OrderStatusPushResult;
import fu.osms.sync.order.OrderStatusPushStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OrderStatusTransitionPolicy
 * 
 * Tests the business logic for order status transitions including:
 * - Platform-specific validation (TikTok, Shopify, Lazada)
 * - Status transition rules
 * - Auto-mark paid logic
 * - Cancellation rules
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderStatusTransitionPolicy Tests")
class OrderStatusTransitionPolicyTest {

    @Mock
    private OrderStockDeliveryReadinessService readinessService;

    private OrderStatusTransitionPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new OrderStatusTransitionPolicy(readinessService);
    }

    // ========== validate() Tests ==========

    @Nested
    @DisplayName("validate() - Basic Status Transitions")
    class ValidateBasicTests {

        @Test
        @DisplayName("Should throw when trying to cancel via status update endpoint")
        void shouldThrowWhenCancellingViaStatusUpdate() {
            Order order = createOrder(PlatformType.MANUAL, OrderStatus.PENDING);
            UUID orderId = UUID.randomUUID();

            assertThatThrownBy(() -> policy.validate(order, OrderStatus.PENDING, OrderStatus.CANCELLED, orderId))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("Hãy sử dụng chức năng Hủy đơn");
        }

        @Test
        @DisplayName("Should allow valid transition from PENDING to CONFIRMED for MANUAL orders")
        void shouldAllowPendingToConfirmedForManual() {
            Order order = createOrder(PlatformType.MANUAL, OrderStatus.PENDING);
            UUID orderId = UUID.randomUUID();

            assertThatCode(() -> policy.validate(order, OrderStatus.PENDING, OrderStatus.CONFIRMED, orderId))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should allow valid transition from CONFIRMED to PROCESSING")
        void shouldAllowConfirmedToProcessing() {
            Order order = createOrder(PlatformType.MANUAL, OrderStatus.CONFIRMED);
            UUID orderId = UUID.randomUUID();

            assertThatCode(() -> policy.validate(order, OrderStatus.CONFIRMED, OrderStatus.PROCESSING, orderId))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should throw when transitioning to SHIPPED without being in PROCESSING")
        void shouldThrowWhenShippingFromNonProcessing() {
            Order order = createOrder(PlatformType.MANUAL, OrderStatus.CONFIRMED);
            UUID orderId = UUID.randomUUID();

            assertThatThrownBy(() -> policy.validate(order, OrderStatus.CONFIRMED, OrderStatus.SHIPPED, orderId))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("Không thể chuyển đổi trạng thái");
        }

        @Test
        @DisplayName("Should call readiness service when transitioning to SHIPPED from PROCESSING")
        void shouldCallReadinessServiceWhenShipping() {
            Order order = createOrder(PlatformType.MANUAL, OrderStatus.PROCESSING);
            UUID orderId = UUID.randomUUID();
            doNothing().when(readinessService).requireReadyForShipment(orderId);

            policy.validate(order, OrderStatus.PROCESSING, OrderStatus.SHIPPED, orderId);

            verify(readinessService).requireReadyForShipment(orderId);
        }
    }

    // ========== validate() - TikTok Platform Rules ==========

    @Nested
    @DisplayName("validate() - TikTok Platform Rules")
    class ValidateTikTokTests {

        @Test
        @DisplayName("Should throw when TikTok order not in AWAITING_SHIPMENT status")
        void shouldThrowWhenTikTokNotAwaitingShipment() {
            // Create TikTok order with rawOrderStatus = PENDING (not AWAITING_SHIPMENT)
            Order order = createTikTokOrderWithRawStatus("PENDING");
            UUID orderId = UUID.randomUUID();

            assertThatThrownBy(() -> policy.validate(order, OrderStatus.PENDING, OrderStatus.CONFIRMED, orderId))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("TikTok chưa chuyển đơn sang AWAITING_SHIPMENT");
        }

        @Test
        @DisplayName("Should allow TikTok order in AWAITING_SHIPMENT to be confirmed")
        void shouldAllowTikTokAwaitingShipmentToConfirm() {
            Order order = createTikTokOrderWithRawStatus("AWAITING_SHIPMENT");
            UUID orderId = UUID.randomUUID();

            assertThatCode(() -> policy.validate(order, OrderStatus.PENDING, OrderStatus.CONFIRMED, orderId))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should allow TikTok order in AWAITING_SHIPMENT to be processed")
        void shouldAllowTikTokAwaitingShipmentToProcess() {
            Order order = createTikTokOrderWithRawStatus("AWAITING_SHIPMENT");
            UUID orderId = UUID.randomUUID();

            assertThatCode(() -> policy.validate(order, OrderStatus.PENDING, OrderStatus.PROCESSING, orderId))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should allow TikTok order in AWAITING_SHIPMENT to be shipped directly")
        void shouldAllowTikTokAwaitingShipmentToShip() {
            // Must transition from PROCESSING to SHIPPED (not PENDING to SHIPPED)
            Order order = createTikTokOrderWithRawStatus("AWAITING_SHIPMENT");
            order.setStatus(OrderStatus.PROCESSING); // Start from PROCESSING
            UUID orderId = UUID.randomUUID();
            doNothing().when(readinessService).requireReadyForShipment(orderId);

            policy.validate(order, OrderStatus.PROCESSING, OrderStatus.SHIPPED, orderId);

            verify(readinessService).requireReadyForShipment(orderId);
        }

        @Test
        @DisplayName("Should not validate TikTok rules when not transitioning from PENDING")
        void shouldNotValidateTikTokWhenNotPending() {
            // When transitioning from CONFIRMED, TikTok validation is skipped
            Order order = createTikTokOrderWithRawStatus("UNKNOWN");
            order.setStatus(OrderStatus.CONFIRMED);
            UUID orderId = UUID.randomUUID();

            assertThatCode(() -> policy.validate(order, OrderStatus.CONFIRMED, OrderStatus.PROCESSING, orderId))
                    .doesNotThrowAnyException();
        }
    }

    // ========== shouldBlockLocalUpdate() Tests ==========

    @Nested
    @DisplayName("shouldBlockLocalUpdate() Tests")
    class ShouldBlockLocalUpdateTests {

        @Test
        @DisplayName("Should not block for MANUAL orders")
        void shouldNotBlockForManualOrders() {
            Order order = createPlatformOrder(PlatformType.MANUAL, OrderStatus.PENDING);
            OrderStatusPushResult pushResult = OrderStatusPushResult.success("OK", null);

            assertThat(policy.shouldBlockLocalUpdate(order, OrderStatus.CONFIRMED, pushResult)).isFalse();
        }

        @Test
        @DisplayName("Should not block for null platform")
        void shouldNotBlockForNullPlatform() {
            Order order = new Order();
            order.setPlatform(null);
            order.setPlatformMetadata(new HashMap<>());
            OrderStatusPushResult pushResult = OrderStatusPushResult.success("OK", null);

            assertThat(policy.shouldBlockLocalUpdate(order, OrderStatus.CONFIRMED, pushResult)).isFalse();
        }

        @Test
        @DisplayName("Should block for LAZADA when push fails")
        void shouldBlockForLazadaWhenPushFails() {
            Order order = createPlatformOrder(PlatformType.LAZADA, OrderStatus.PENDING);
            OrderStatusPushResult pushResult = OrderStatusPushResult.failed("Failed");

            // LAZADA requires push to succeed for PROCESSING transition
            assertThat(policy.shouldBlockLocalUpdate(order, OrderStatus.PROCESSING, pushResult)).isTrue();
        }

        @Test
        @DisplayName("Should not block for LAZADA when push succeeds")
        void shouldNotBlockForLazadaWhenPushSucceeds() {
            Order order = createPlatformOrder(PlatformType.LAZADA, OrderStatus.PENDING);
            OrderStatusPushResult pushResult = OrderStatusPushResult.success("OK", null);

            assertThat(policy.shouldBlockLocalUpdate(order, OrderStatus.PROCESSING, pushResult)).isFalse();
        }

        @Test
        @DisplayName("Should block for SHOPIFY when push is skipped")
        void shouldBlockForShopifyWhenSkipped() {
            Order order = createPlatformOrder(PlatformType.SHOPIFY, OrderStatus.PROCESSING);
            OrderStatusPushResult pushResult = OrderStatusPushResult.skipped("Skipped");

            // For SHOPIFY (strict platform), block if NOT success (skipped counts as not success)
            assertThat(policy.shouldBlockLocalUpdate(order, OrderStatus.SHIPPED, pushResult)).isTrue();
        }

        @Test
        @DisplayName("Should not block for SHOPIFY when push succeeds")
        void shouldNotBlockForShopifyWhenSucceeds() {
            Order order = createPlatformOrder(PlatformType.SHOPIFY, OrderStatus.PROCESSING);
            OrderStatusPushResult pushResult = OrderStatusPushResult.success("OK", null);

            assertThat(policy.shouldBlockLocalUpdate(order, OrderStatus.SHIPPED, pushResult)).isFalse();
        }
    }

    // ========== shouldAutoMarkPaid() Tests ==========

    @Nested
    @DisplayName("shouldAutoMarkPaid() Tests")
    class ShouldAutoMarkPaidTests {

        @Test
        @DisplayName("Should auto mark paid when MANUAL order is DELIVERED and UNPAID")
        void shouldAutoMarkPaidForManualDeliveredUnpaid() {
            Order order = createPlatformOrder(PlatformType.MANUAL, OrderStatus.DELIVERED);
            order.setPaymentStatus("UNPAID");

            assertThat(policy.shouldAutoMarkPaid(order, OrderStatus.DELIVERED)).isTrue();
        }

        @Test
        @DisplayName("Should not auto mark paid for platform orders")
        void shouldNotAutoMarkPaidForPlatformOrders() {
            // Platform orders need channel set to be considered platform orders
            Order order = createPlatformOrder(PlatformType.TIKTOK, OrderStatus.DELIVERED);
            order.setPaymentStatus("UNPAID");

            // Platform orders should not auto-mark paid
            assertThat(policy.shouldAutoMarkPaid(order, OrderStatus.DELIVERED)).isFalse();
        }

        @Test
        @DisplayName("Should not auto mark paid when already PAID")
        void shouldNotAutoMarkPaidWhenAlreadyPaid() {
            Order order = createPlatformOrder(PlatformType.MANUAL, OrderStatus.DELIVERED);
            order.setPaymentStatus("PAID");

            assertThat(policy.shouldAutoMarkPaid(order, OrderStatus.DELIVERED)).isFalse();
        }

        @Test
        @DisplayName("Should not auto mark paid for non-DELIVERED status")
        void shouldNotAutoMarkPaidForNonDelivered() {
            Order order = createPlatformOrder(PlatformType.MANUAL, OrderStatus.SHIPPED);
            order.setPaymentStatus("UNPAID");

            assertThat(policy.shouldAutoMarkPaid(order, OrderStatus.SHIPPED)).isFalse();
        }
    }

    // ========== isPlatformOrder() Tests ==========

    @Nested
    @DisplayName("isPlatformOrder() Tests")
    class IsPlatformOrderTests {

        @Test
        @DisplayName("Should return false for MANUAL orders")
        void shouldReturnFalseForManual() {
            Order order = createPlatformOrder(PlatformType.MANUAL, OrderStatus.PENDING);
            assertThat(policy.isPlatformOrder(order)).isFalse();
        }

        @Test
        @DisplayName("Should return true for SHOPIFY orders")
        void shouldReturnTrueForShopify() {
            Order order = createPlatformOrder(PlatformType.SHOPIFY, OrderStatus.PENDING);
            assertThat(policy.isPlatformOrder(order)).isTrue();
        }

        @Test
        @DisplayName("Should return true for TIKTOK orders")
        void shouldReturnTrueForTikTok() {
            Order order = createPlatformOrder(PlatformType.TIKTOK, OrderStatus.PENDING);
            assertThat(policy.isPlatformOrder(order)).isTrue();
        }

        @Test
        @DisplayName("Should return true for LAZADA orders")
        void shouldReturnTrueForLazada() {
            Order order = createPlatformOrder(PlatformType.LAZADA, OrderStatus.PENDING);
            assertThat(policy.isPlatformOrder(order)).isTrue();
        }

        @Test
        @DisplayName("Should return false for null platform even with channel")
        void shouldReturnFalseForNullPlatform() {
            Order order = new Order();
            order.setPlatform(null);
            // Channel is not null but platform is null
            assertThat(policy.isPlatformOrder(order)).isFalse();
        }
    }

    // ========== requiresTextCancelReason() Tests ==========

    @Nested
    @DisplayName("requiresTextCancelReason() Tests")
    class RequiresTextCancelReasonTests {

        @Test
        @DisplayName("Should not require text reason for LAZADA")
        void shouldNotRequireTextReasonForLazada() {
            Order order = createOrder(PlatformType.LAZADA, OrderStatus.PENDING);
            assertThat(policy.requiresTextCancelReason(order)).isFalse();
        }

        @Test
        @DisplayName("Should not require text reason for SHOPIFY")
        void shouldNotRequireTextReasonForShopify() {
            Order order = createOrder(PlatformType.SHOPIFY, OrderStatus.PENDING);
            assertThat(policy.requiresTextCancelReason(order)).isFalse();
        }

        @Test
        @DisplayName("Should not require text reason for TIKTOK")
        void shouldNotRequireTextReasonForTikTok() {
            Order order = createOrder(PlatformType.TIKTOK, OrderStatus.PENDING);
            assertThat(policy.requiresTextCancelReason(order)).isFalse();
        }

        @Test
        @DisplayName("Should require text reason for MANUAL orders")
        void shouldRequireTextReasonForManual() {
            Order order = createOrder(PlatformType.MANUAL, OrderStatus.PENDING);
            assertThat(policy.requiresTextCancelReason(order)).isTrue();
        }
    }

    // ========== isTikTokCancellationPending() Tests ==========

    @Nested
    @DisplayName("isTikTokCancellationPending() Tests")
    class IsTikTokCancellationPendingTests {

        @Test
        @DisplayName("Should return false when platform metadata is null")
        void shouldReturnFalseWhenMetadataNull() {
            Order order = new Order();
            order.setPlatform(PlatformType.TIKTOK);
            order.setPlatformMetadata(null);
            assertThat(policy.isTikTokCancellationPending(order)).isFalse();
        }

        @Test
        @DisplayName("Should return false when tiktok key is missing")
        void shouldReturnFalseWhenTiktokKeyMissing() {
            Order order = new Order();
            order.setPlatform(PlatformType.TIKTOK);
            order.setPlatformMetadata(new HashMap<>());
            assertThat(policy.isTikTokCancellationPending(order)).isFalse();
        }

        @Test
        @DisplayName("Should return false when pendingConfirmation is false Boolean")
        void shouldReturnFalseWhenPendingConfirmationFalseBoolean() {
            Map<String, Object> tikTokMeta = new HashMap<>();
            tikTokMeta.put("pendingConfirmation", Boolean.FALSE);
            Map<String, Object> meta = new HashMap<>();
            meta.put("tiktok", tikTokMeta);
            
            Order order = new Order();
            order.setPlatform(PlatformType.TIKTOK);
            order.setPlatformMetadata(meta);
            
            assertThat(policy.isTikTokCancellationPending(order)).isFalse();
        }

        @Test
        @DisplayName("Should return true when pendingConfirmation is true Boolean")
        void shouldReturnTrueWhenPendingConfirmationTrueBoolean() {
            Map<String, Object> tikTokMeta = new HashMap<>();
            tikTokMeta.put("pendingConfirmation", Boolean.TRUE);
            Map<String, Object> meta = new HashMap<>();
            meta.put("tiktok", tikTokMeta);
            
            Order order = new Order();
            order.setPlatform(PlatformType.TIKTOK);
            order.setPlatformMetadata(meta);
            
            assertThat(policy.isTikTokCancellationPending(order)).isTrue();
        }

        @Test
        @DisplayName("Should parse string 'true' value correctly")
        void shouldParseStringTrueValue() {
            Map<String, Object> tikTokMeta = new HashMap<>();
            tikTokMeta.put("pendingConfirmation", "true");
            Map<String, Object> meta = new HashMap<>();
            meta.put("tiktok", tikTokMeta);
            
            Order order = new Order();
            order.setPlatform(PlatformType.TIKTOK);
            order.setPlatformMetadata(meta);
            
            assertThat(policy.isTikTokCancellationPending(order)).isTrue();
        }
    }

    // ========== Helper Methods ==========

    private Order createOrder(PlatformType platform, OrderStatus status) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setPlatform(platform);
        order.setStatus(status);
        order.setPlatformMetadata(new HashMap<>());
        return order;
    }

    /**
     * Creates an order with channel set (required for platform order detection).
     * Uses a mock Channel object to satisfy the isPlatformOrder() check.
     */
    private Order createPlatformOrder(PlatformType platform, OrderStatus status) {
        Order order = createOrder(platform, status);
        // Create a mock channel (just need non-null for isPlatformOrder check)
        order.setChannel(new fu.osms.channel.entity.Channel());
        return order;
    }

    /**
     * Creates a TikTok order with the given rawOrderStatus in platform metadata.
     * The metadata structure is: { "tiktok": { "rawOrderStatus": "..." } }
     */
    private Order createTikTokOrderWithRawStatus(String rawOrderStatus) {
        Map<String, Object> tikTokMeta = new HashMap<>();
        tikTokMeta.put("rawOrderStatus", rawOrderStatus);
        Map<String, Object> meta = new HashMap<>();
        meta.put("tiktok", tikTokMeta);

        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setPlatform(PlatformType.TIKTOK);
        order.setStatus(OrderStatus.PENDING);
        order.setPlatformMetadata(meta);
        // TikTok orders need channel for platform detection
        order.setChannel(new fu.osms.channel.entity.Channel());
        return order;
    }
}
