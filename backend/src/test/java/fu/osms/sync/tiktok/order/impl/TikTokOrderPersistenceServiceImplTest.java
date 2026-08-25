package fu.osms.sync.tiktok.order.impl;

import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelProductVariant;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.common.enums.PlatformType;
import fu.osms.order.entity.Order;
import fu.osms.order.enums.OrderStatus;
import fu.osms.order.repository.OrderItemRepository;
import fu.osms.order.repository.OrderRepository;
import fu.osms.sync.order.importing.OrderImportResult;
import fu.osms.sync.order.importing.OrderUpsertResult;
import fu.osms.sync.order.importing.OrderUpsertSupport;
import fu.osms.sync.tiktok.order.TikTokDispatchSlaCalculator;
import fu.osms.sync.tiktok.order.TikTokOrderMetadataMapper;
import fu.osms.sync.tiktok.order.TikTokOrderWriteContext;
import fu.osms.sync.tiktok.order.TikTokOrderWriteModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TikTokOrderPersistenceServiceImpl Smoke Tests")
class TikTokOrderPersistenceServiceImplTest {

    @Mock private OrderUpsertSupport upsertSupport;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository itemRepository;
    @Mock private ChannelProductVariantRepository channelVariantRepository;
    @Mock private TikTokOrderMetadataMapper metadataMapper;
    @Mock private TikTokDispatchSlaCalculator slaCalculator;
    @Mock private ApplicationEventPublisher eventPublisher;

    private TikTokOrderPersistenceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TikTokOrderPersistenceServiceImpl(
                upsertSupport, orderRepository, itemRepository,
                channelVariantRepository, metadataMapper, slaCalculator, eventPublisher);
    }

    private Channel channel() {
        return Channel.builder()
                .id(UUID.randomUUID())
                .displayName("TikTok Shop")
                .platform(PlatformType.TIKTOK)
                .build();
    }

    private Order order(Channel channel, OrderStatus status, String paymentStatus) {
        return Order.builder()
                .id(UUID.randomUUID())
                .channel(channel)
                .status(status)
                .paymentStatus(paymentStatus)
                .build();
    }

    private TikTokOrderWriteModel model(String extId, OrderStatus status, String paymentStatus) {
        return new TikTokOrderWriteModel(
                extId, (OffsetDateTime) null, status, "RAW_STATUS",
                paymentStatus,
                "Buyer", "0901", Map.of("city", "HCMC"),
                BigDecimal.valueOf(100), BigDecimal.ZERO, BigDecimal.ZERO,
                "VND", "note", null, "track-1",
                System.currentTimeMillis(),
                null, null, null, null, null, null, null,
                Map.of(),
                List.of(new TikTokOrderWriteModel.Item("ext-itm-1", "ext-var-1", "SKU-1", "P1", 1, BigDecimal.valueOf(100), BigDecimal.ZERO))
        );
    }

    @Test
    @DisplayName("write: reports CREATED outcome when upsert creates a new order")
    void write_created() {
        Channel channel = channel();
        Order order = order(channel, OrderStatus.PENDING, "UNPAID");
        TikTokOrderWriteModel m = model("EXT-TT-1", OrderStatus.PENDING, "UNPAID");
        TikTokOrderWriteContext ctx = TikTokOrderWriteContext.manual(channel);

        when(upsertSupport.ensureAndLock(channel, "EXT-TT-1", PlatformType.TIKTOK))
                .thenReturn(new OrderUpsertResult(order, true));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(metadataMapper.merge(any(), any(), any(), any(Boolean.class))).thenReturn(Map.of());

        var outcome = service.write(ctx, m);

        assertThat(outcome.created()).isTrue();
        assertThat(outcome.result()).isEqualTo(OrderImportResult.CREATED);
        verify(itemRepository).deleteByOrderId(order.getId());
    }

    @Test
    @DisplayName("write: reports UPDATED outcome for an existing order")
    void write_updated() {
        Channel channel = channel();
        Order order = order(channel, OrderStatus.PENDING, "PAID");
        TikTokOrderWriteModel m = model("EXT-TT-1", OrderStatus.PROCESSING, "PAID");
        TikTokOrderWriteContext ctx = TikTokOrderWriteContext.manual(channel);

        when(upsertSupport.ensureAndLock(channel, "EXT-TT-1", PlatformType.TIKTOK))
                .thenReturn(new OrderUpsertResult(order, false));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(metadataMapper.merge(any(), any(), any(), any(Boolean.class))).thenReturn(Map.of());

        var outcome = service.write(ctx, m);

        assertThat(outcome.created()).isFalse();
        assertThat(outcome.result()).isEqualTo(OrderImportResult.UPDATED);
    }

    @Test
    @DisplayName("write: resolves externalVariantId via channelVariantRepository when present")
    void write_resolvesVariant() {
        Channel channel = channel();
        Order order = order(channel, OrderStatus.PENDING, "PAID");
        TikTokOrderWriteModel m = model("EXT-TT-1", OrderStatus.PENDING, "PAID");
        TikTokOrderWriteContext ctx = TikTokOrderWriteContext.manual(channel);
        ChannelProductVariant mapping = ChannelProductVariant.builder()
                .id(UUID.randomUUID())
                .variant(fu.osms.catalog.entity.ProductVariant.builder().id(UUID.randomUUID()).build())
                .build();

        when(upsertSupport.ensureAndLock(channel, "EXT-TT-1", PlatformType.TIKTOK))
                .thenReturn(new OrderUpsertResult(order, false));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(metadataMapper.merge(any(), any(), any(), any(Boolean.class))).thenReturn(Map.of());
        when(channelVariantRepository.findActiveByChannelIdAndExternalVariantId(channel.getId(), "ext-var-1"))
                .thenReturn(java.util.Optional.of(mapping));

        service.write(ctx, m);

        verify(channelVariantRepository)
                .findActiveByChannelIdAndExternalVariantId(channel.getId(), "ext-var-1");
    }
}
