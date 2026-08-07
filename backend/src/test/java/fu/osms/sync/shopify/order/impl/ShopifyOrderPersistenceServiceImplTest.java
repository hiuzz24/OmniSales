package fu.osms.sync.shopify.order.impl;

import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelProductVariant;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.common.enums.PlatformType;
import fu.osms.order.entity.Order;
import fu.osms.order.enums.OrderStatus;
import fu.osms.order.repository.OrderItemRepository;
import fu.osms.order.repository.OrderRepository;
import fu.osms.sync.order.importing.OrderUpsertResult;
import fu.osms.sync.order.importing.OrderUpsertSupport;
import fu.osms.sync.shopify.order.ShopifyOrderWriteModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ShopifyOrderPersistenceServiceImpl Tests")
class ShopifyOrderPersistenceServiceImplTest {

    @Mock private OrderUpsertSupport upsertSupport;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository itemRepository;
    @Mock private ChannelProductVariantRepository channelVariantRepository;

    private ShopifyOrderPersistenceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ShopifyOrderPersistenceServiceImpl(
                upsertSupport, orderRepository, itemRepository, channelVariantRepository);
    }

    private Channel channel() {
        return Channel.builder()
                .id(UUID.randomUUID())
                .displayName("Shopify Store")
                .platform(PlatformType.SHOPIFY)
                .build();
    }

    private ShopifyOrderWriteModel model(String externalOrderId, OrderStatus status, String paymentStatus) {
        return new ShopifyOrderWriteModel(
                externalOrderId, null, status, paymentStatus,
                "Buyer", "0901", Map.of("city", "HCMC"),
                BigDecimal.valueOf(100), BigDecimal.ZERO, BigDecimal.ZERO,
                "VND", "note", "track-1", null,
                List.of(new ShopifyOrderWriteModel.Item("ext-1", "ext-var-1", "SKU-1", "P1", 2, BigDecimal.valueOf(50), BigDecimal.ZERO))
        );
    }

    private Order order(Channel channel, OrderStatus status, String paymentStatus) {
        return Order.builder()
                .id(UUID.randomUUID())
                .channel(channel)
                .status(status)
                .paymentStatus(paymentStatus)
                .build();
    }

    @Test
    @DisplayName("write: marks outcome CREATED when upsert creates a new order")
    void write_created() {
        Channel channel = channel();
        Order order = order(channel, OrderStatus.PENDING, "UNPAID");
        ShopifyOrderWriteModel m = model("EXT-1", OrderStatus.PENDING, "UNPAID");

        when(upsertSupport.ensureAndLock(channel, "EXT-1", PlatformType.SHOPIFY))
                .thenReturn(new OrderUpsertResult(order, true));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        var outcome = service.write(channel, m);

        assertThat(outcome.created()).isTrue();
        verify(itemRepository).deleteByOrderId(order.getId());
        verify(itemRepository).saveAll(any());
    }

    @Test
    @DisplayName("write: resolves externalVariantId through channelVariantRepository")
    void write_resolvesVariant() {
        Channel channel = channel();
        Order order = order(channel, OrderStatus.PENDING, "UNPAID");
        ShopifyOrderWriteModel m = model("EXT-1", OrderStatus.PENDING, "PAID");
        ChannelProductVariant mapping = ChannelProductVariant.builder()
                .id(UUID.randomUUID())
                .variant(fu.osms.catalog.entity.ProductVariant.builder()
                        .id(UUID.randomUUID()).costPrice(BigDecimal.TEN).build())
                .build();

        when(upsertSupport.ensureAndLock(channel, "EXT-1", PlatformType.SHOPIFY))
                .thenReturn(new OrderUpsertResult(order, false));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(channelVariantRepository.findActiveByChannelIdAndExternalVariantId(eq(channel.getId()), eq("ext-var-1")))
                .thenReturn(Optional.of(mapping));

        var outcome = service.write(channel, m);

        verify(channelVariantRepository).findActiveByChannelIdAndExternalVariantId(channel.getId(), "ext-var-1");
        assertThat(outcome.paymentBecamePaid()).isTrue();
    }

    @Test
    @DisplayName("write: rejects masked buyer name containing '***'")
    void write_maskedBuyerName() {
        Channel channel = channel();
        Order order = order(channel, OrderStatus.PENDING, "UNPAID");
        order.setBuyerName("Real Name");
        ShopifyOrderWriteModel m = new ShopifyOrderWriteModel(
                "EXT-1", null, OrderStatus.PENDING, "UNPAID",
                "*** Buyer ***", "0901", null,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "VND", null, null, null,
                List.of());

        when(upsertSupport.ensureAndLock(channel, "EXT-1", PlatformType.SHOPIFY))
                .thenReturn(new OrderUpsertResult(order, false));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.write(channel, m);

        verify(orderRepository).save(any(Order.class));
        assertThat(order.getBuyerName()).isEqualTo("Real Name");
    }

    @Test
    @DisplayName("write: keeps existing shipping address when incoming has masked values")
    void write_addressMasked() {
        Channel channel = channel();
        Map<String, Object> current = Map.of("city", "Old City");
        Order order = order(channel, OrderStatus.PENDING, "UNPAID");
        order.setShippingAddress(current);
        ShopifyOrderWriteModel m = new ShopifyOrderWriteModel(
                "EXT-1", null, OrderStatus.PENDING, "UNPAID",
                null, null, Map.of("city", "*** masked ***"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "VND", null, null, null,
                List.of());

        when(upsertSupport.ensureAndLock(channel, "EXT-1", PlatformType.SHOPIFY))
                .thenReturn(new OrderUpsertResult(order, false));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.write(channel, m);

        assertThat(order.getShippingAddress()).isEqualTo(current);
    }

    @Test
    @DisplayName("write: keeps order status when current is CANCELLED (cannot revive a cancelled order)")
    void write_cancelledLock() {
        Channel channel = channel();
        Order order = order(channel, OrderStatus.CANCELLED, "PAID");
        ShopifyOrderWriteModel m = model("EXT-1", OrderStatus.PENDING, "PAID");

        when(upsertSupport.ensureAndLock(channel, "EXT-1", PlatformType.SHOPIFY))
                .thenReturn(new OrderUpsertResult(order, false));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.write(channel, m);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("write: transitions to CANCELLED when incoming status is CANCELLED")
    void write_transitionToCancelled() {
        Channel channel = channel();
        Order order = order(channel, OrderStatus.PENDING, "PAID");
        ShopifyOrderWriteModel m = model("EXT-1", OrderStatus.CANCELLED, "PAID");

        when(upsertSupport.ensureAndLock(channel, "EXT-1", PlatformType.SHOPIFY))
                .thenReturn(new OrderUpsertResult(order, false));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        var outcome = service.write(channel, m);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(outcome.becameCancelled()).isTrue();
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