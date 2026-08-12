package fu.osms.sync.lazada.order.impl;

import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelProductVariant;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.common.enums.PlatformType;
import fu.osms.order.entity.Order;
import fu.osms.order.enums.OrderStatus;
import fu.osms.order.repository.OrderItemRepository;
import fu.osms.order.repository.OrderRepository;
import fu.osms.sync.lazada.order.LazadaOrderWriteModel;
import fu.osms.sync.order.importing.OrderUpsertResult;
import fu.osms.sync.order.importing.OrderUpsertSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LazadaOrderPersistenceServiceImpl Tests")
class LazadaOrderPersistenceServiceImplTest {

    @Mock private OrderUpsertSupport upsertSupport;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private ChannelProductVariantRepository channelVariantRepository;

    private LazadaOrderPersistenceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LazadaOrderPersistenceServiceImpl(
                upsertSupport, orderRepository, orderItemRepository, channelVariantRepository);
    }

    private Channel channel() {
        return Channel.builder().id(UUID.randomUUID()).displayName("Lazada VN").platform(PlatformType.LAZADA).build();
    }

    private Order order(Channel channel, OrderStatus status, String paymentStatus) {
        return Order.builder()
                .id(UUID.randomUUID())
                .channel(channel)
                .status(status)
                .paymentStatus(paymentStatus)
                .build();
    }

    private static final OffsetDateTime CREATED_AT = OffsetDateTime.parse("2026-08-01T10:00:00+07:00");

    private LazadaOrderWriteModel model(String extOrderId, String extVariantId) {
        return  new LazadaOrderWriteModel(
                extOrderId,
                CREATED_AT,
                OrderStatus.PENDING,
                "UNPAID",
                "Buyer",
                "0901234567",
                Map.of("city", "HCMC"),
                BigDecimal.valueOf(100),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "VND",
                "note",
                "track-1",
                List.of(new LazadaOrderWriteModel.Item("ext-itm-1", extVariantId, "SKU-1", "P1", 2, BigDecimal.valueOf(50), BigDecimal.ZERO))
        );
    }

    @Test
    @DisplayName("write: creates order with status PENDING and reports CREATED outcome on first import")
    void write_created() {
        Channel channel = channel();
        Order order = order(channel, OrderStatus.PENDING, "UNPAID");
        LazadaOrderWriteModel m = model("EXT-1", null);

        when(upsertSupport.ensureAndLock(channel, "EXT-1", PlatformType.LAZADA))
                .thenReturn(new OrderUpsertResult(order, true));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        var outcome = service.write(channel, m);

        assertThat(outcome.orderId()).isEqualTo(order.getId());
        assertThat(outcome.created()).isTrue();
        assertThat(outcome.result().name()).isEqualTo("CREATED");
        verify(orderItemRepository).deleteByOrderId(order.getId());
        verify(orderItemRepository).saveAll(any());
    }

    @Test
    @DisplayName("write: detects payment transition UNPAID -> PAID and marks paymentBecamePaid")
    void write_paymentTransition() {
        Channel channel = channel();
        Order order = order(channel, OrderStatus.PENDING, "UNPAID");
        LazadaOrderWriteModel m = new LazadaOrderWriteModel(
                "EXT-1", CREATED_AT, OrderStatus.PENDING, "PAID", null, null, null,
                BigDecimal.valueOf(100), BigDecimal.ZERO, BigDecimal.ZERO, "VND", null, null,
                List.of(new LazadaOrderWriteModel.Item(null, null, "SKU-1", "P1", 1, BigDecimal.valueOf(100), BigDecimal.ZERO))
        );

        when(upsertSupport.ensureAndLock(channel, "EXT-1", PlatformType.LAZADA))
                .thenReturn(new OrderUpsertResult(order, false));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        var outcome = service.write(channel, m);

        assertThat(outcome.paymentBecamePaid()).isTrue();
    }

    @Test
    @DisplayName("write: detects CANCELLED transition and marks becameCancelled")
    void write_cancelTransition() {
        Channel channel = channel();
        Order order = order(channel, OrderStatus.PENDING, "PAID");
        LazadaOrderWriteModel m = new LazadaOrderWriteModel(
                "EXT-1", CREATED_AT, OrderStatus.CANCELLED, "PAID", null, null, null,
                BigDecimal.valueOf(100), BigDecimal.ZERO, BigDecimal.ZERO, "VND", null, null,
                List.of()
        );

        when(upsertSupport.ensureAndLock(channel, "EXT-1", PlatformType.LAZADA))
                .thenReturn(new OrderUpsertResult(order, false));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        var outcome = service.write(channel, m);

        assertThat(outcome.becameCancelled()).isTrue();
    }

    @Test
    @DisplayName("write: keeps existing address when incoming shippingAddress contains masked values")
    void write_addressMasked_keepsCurrent() {
        Channel channel = channel();
        Map<String, Object> current = Map.of("city", "Old City");
        Order order = order(channel, OrderStatus.PENDING, "UNPAID");
        order.setShippingAddress(current);
        LazadaOrderWriteModel m = new LazadaOrderWriteModel(
                "EXT-1", CREATED_AT, OrderStatus.PENDING, "UNPAID", null, null,
                Map.of("city", "*** masked ***"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "VND", null, null,
                List.of()
        );

        when(upsertSupport.ensureAndLock(channel, "EXT-1", PlatformType.LAZADA))
                .thenReturn(new OrderUpsertResult(order, false));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.write(channel, m);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getShippingAddress()).isEqualTo(current);
    }

    @Test
    @DisplayName("write: accepts non-masked incoming address when current is empty")
    void write_addressReplaceEmpty() {
        Channel channel = channel();
        Order order = order(channel, OrderStatus.PENDING, "UNPAID");
        order.setShippingAddress(null);
        Map<String, Object> incoming = Map.of("city", "HCMC");
        LazadaOrderWriteModel m = new LazadaOrderWriteModel(
                "EXT-1", CREATED_AT, OrderStatus.PENDING, "UNPAID", null, null, incoming,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "VND", null, null,
                List.of()
        );

        when(upsertSupport.ensureAndLock(channel, "EXT-1", PlatformType.LAZADA))
                .thenReturn(new OrderUpsertResult(order, false));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.write(channel, m);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getShippingAddress()).isEqualTo(incoming);
    }

    @Test
    @DisplayName("write: rejects masked buyer name containing '***'")
    void write_maskedBuyerName() {
        Channel channel = channel();
        Order order = order(channel, OrderStatus.PENDING, "UNPAID");
        order.setBuyerName("Real Name");
        LazadaOrderWriteModel m = new LazadaOrderWriteModel(
                "EXT-1", CREATED_AT, OrderStatus.PENDING, "UNPAID", "*** Buyer ***", null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "VND", null, null,
                List.of()
        );

        when(upsertSupport.ensureAndLock(channel, "EXT-1", PlatformType.LAZADA))
                .thenReturn(new OrderUpsertResult(order, false));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.write(channel, m);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getBuyerName()).isEqualTo("Real Name");
    }

    @Test
    @DisplayName("write: resolves externalVariantId via channelVariantRepository when present")
    void write_resolvesExternalVariant() {
        Channel channel = channel();
        Order order = order(channel, OrderStatus.PENDING, "UNPAID");
        UUID variantId = UUID.randomUUID();
        ChannelProductVariant mapping = ChannelProductVariant.builder()
                .id(UUID.randomUUID())
                .variant(fu.osms.catalog.entity.ProductVariant.builder().id(variantId).costPrice(BigDecimal.TEN).build())
                .build();
        LazadaOrderWriteModel m = model("EXT-1", "EXT-VAR-1");

        when(upsertSupport.ensureAndLock(channel, "EXT-1", PlatformType.LAZADA))
                .thenReturn(new OrderUpsertResult(order, false));
        when(channelVariantRepository.findActiveByChannelIdAndExternalVariantId(eq(channel.getId()), eq("EXT-VAR-1")))
                .thenReturn(Optional.of(mapping));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.write(channel, m);

        verify(channelVariantRepository).findActiveByChannelIdAndExternalVariantId(channel.getId(), "EXT-VAR-1");
    }

    @Test
    @DisplayName("getOrder: returns the order from the repository by id")
    void getOrder() {
        UUID id = UUID.randomUUID();
        Order order = Order.builder().id(id).build();
        when(orderRepository.findById(id)).thenReturn(Optional.of(order));

        Order result = service.getOrder(new fu.osms.sync.order.importing.OrderImportOutcome(
                id, fu.osms.sync.order.importing.OrderImportResult.UPDATED,
                false, false, false, false, null, null));

        assertThat(result.getId()).isEqualTo(id);
    }
}