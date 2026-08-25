package fu.osms.sync.service.impl;

import fu.osms.channel.dto.response.ChannelImportSyncResponse;
import fu.osms.channel.entity.Channel;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.channel.service.ChannelConnectionValidator;
import fu.osms.common.enums.PlatformType;
import fu.osms.inventory.repository.InventoryIssueRepository;
import fu.osms.inventory.repository.StockReceiveRepository;
import fu.osms.messaging.constants.RabbitMQConstants;
import fu.osms.messaging.publisher.EventPublisher;
import fu.osms.sync.entity.SyncLog;
import fu.osms.sync.lazada.service.LazadaInventoryUpdateService;
import fu.osms.sync.repository.SyncLogRepository;
import fu.osms.sync.service.MarketplaceWarehouseConsistencyService;
import fu.osms.sync.shopify.ShopifyInventoryUpdateService;
import fu.osms.sync.tiktok.TikTokInventoryUpdateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChannelLocalSyncServiceImplTest {

    @Mock private ChannelRepository channelRepository;
    @Mock private ChannelConnectionValidator channelConnectionValidator;
    @Mock private SyncLogRepository syncLogRepository;
    @Mock private LazadaInventoryUpdateService lazadaInventoryUpdateService;
    @Mock private ShopifyInventoryUpdateService shopifyInventoryUpdateService;
    @Mock private TikTokInventoryUpdateService tikTokInventoryUpdateService;
    @Mock private StockReceiveRepository stockReceiveRepository;
    @Mock private InventoryIssueRepository inventoryIssueRepository;
    @Mock private MarketplaceWarehouseConsistencyService marketplaceWarehouseConsistencyService;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private EventPublisher eventPublisher;

    private ChannelLocalSyncServiceImpl service;
    private Channel channel;

    @BeforeEach
    void setUp() {
        service = new ChannelLocalSyncServiceImpl(
                channelRepository,
                channelConnectionValidator,
                syncLogRepository,
                lazadaInventoryUpdateService,
                shopifyInventoryUpdateService,
                tikTokInventoryUpdateService,
                stockReceiveRepository,
                inventoryIssueRepository,
                marketplaceWarehouseConsistencyService,
                transactionTemplate,
                eventPublisher
        );
        channel = Channel.builder()
                .id(UUID.randomUUID())
                .platform(PlatformType.SHOPIFY)
                .displayName("shop.myshopify.com")
                .syncEnabled(true)
                .build();
        when(channelConnectionValidator.requireConnected(channel.getId())).thenReturn(channel);
        when(syncLogRepository.save(any(SyncLog.class))).thenAnswer(invocation -> {
            SyncLog log = invocation.getArgument(0);
            if (log.getId() == null) {
                log.setId(UUID.randomUUID());
            }
            return log;
        });
    }

    @Test
    void reportsNoChangesAndAdvancesApplicationCursorWithoutCallingMarketplace() {
        when(channelRepository.save(any(Channel.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(stockReceiveRepository.findConfirmedVariantIdsUpTo(any(OffsetDateTime.class))).thenReturn(List.of());
        when(inventoryIssueRepository.findConfirmedVariantIdsUpTo(any(OffsetDateTime.class))).thenReturn(List.of());

        ChannelImportSyncResponse response = service.syncLocalChanges(channel.getId());

        assertThat(response.getPushedVariantCount()).isZero();
        assertThat(response.getMessage()).contains("Không có phiếu nhập");
        assertThat(channel.getLastSyncedApplicationAt()).isNotNull();
        verify(shopifyInventoryUpdateService, never())
                .syncChangedAvailableStock(any(), any(), any(), any());
    }

    @Test
    void pushesOnlyVariantsFromConfirmedDocumentsAfterApplicationCursor() {
        when(channelRepository.save(any(Channel.class))).thenAnswer(invocation -> invocation.getArgument(0));
        OffsetDateTime cursor = OffsetDateTime.now().minusHours(1);
        UUID receiptVariantId = UUID.randomUUID();
        UUID issueVariantId = UUID.randomUUID();
        channel.setLastSyncedApplicationAt(cursor);
        when(stockReceiveRepository.findChangedConfirmedVariantIdsBetween(
                eq(cursor), any(OffsetDateTime.class))).thenReturn(List.of(receiptVariantId));
        when(inventoryIssueRepository.findChangedAppliedVariantIdsBetween(
                eq(cursor), any(OffsetDateTime.class))).thenReturn(List.of(issueVariantId));
        when(shopifyInventoryUpdateService.syncChangedAvailableStock(
                eq(channel.getId()), eq(cursor), any(OffsetDateTime.class), any())).thenReturn(2);

        ChannelImportSyncResponse response = service.syncLocalChanges(channel.getId());

        assertThat(response.getProductCount()).isZero();
        assertThat(response.getPushedVariantCount()).isEqualTo(2);
        assertThat(channel.getLastSyncedApplicationAt()).isAfter(cursor);
        verify(shopifyInventoryUpdateService).syncChangedAvailableStock(
                eq(channel.getId()),
                eq(cursor),
                any(OffsetDateTime.class),
                eq(java.util.Set.of(receiptVariantId, issueVariantId))
        );
    }

    @Test
    void enqueuesBrokerRetryAndKeepsCursorWhenManualPushFails() {
        OffsetDateTime cursor = OffsetDateTime.now().minusHours(1);
        UUID variantId = UUID.randomUUID();
        channel.setLastSyncedApplicationAt(cursor);
        when(stockReceiveRepository.findChangedConfirmedVariantIdsBetween(
                eq(cursor), any(OffsetDateTime.class))).thenReturn(List.of(variantId));
        when(inventoryIssueRepository.findChangedAppliedVariantIdsBetween(
                eq(cursor), any(OffsetDateTime.class))).thenReturn(List.of());
        when(shopifyInventoryUpdateService.syncChangedAvailableStock(
                eq(channel.getId()), eq(cursor), any(OffsetDateTime.class), any()))
                .thenThrow(new IllegalStateException("Marketplace API down"));

        Throwable thrown = catchThrowable(() -> service.syncLocalChanges(channel.getId()));

        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(channel.getLastSyncedApplicationAt()).isEqualTo(cursor);
        verify(eventPublisher).publish(
                eq(RabbitMQConstants.CHANNEL_PUSH_RETRY), any());
    }

    @Test
    void doesNotEnqueueAnotherRetryWhenBrokerRetryFails() {
        OffsetDateTime cursor = OffsetDateTime.now().minusHours(1);
        UUID variantId = UUID.randomUUID();
        channel.setLastSyncedApplicationAt(cursor);
        when(stockReceiveRepository.findChangedConfirmedVariantIdsBetween(
                eq(cursor), any(OffsetDateTime.class))).thenReturn(List.of(variantId));
        when(inventoryIssueRepository.findChangedAppliedVariantIdsBetween(
                eq(cursor), any(OffsetDateTime.class))).thenReturn(List.of());
        when(shopifyInventoryUpdateService.syncChangedAvailableStock(
                eq(channel.getId()), eq(cursor), any(OffsetDateTime.class), any()))
                .thenThrow(new IllegalStateException("Marketplace API down"));

        Throwable thrown = catchThrowable(() -> service.retryLocalSync(channel.getId()));

        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(channel.getLastSyncedApplicationAt()).isEqualTo(cursor);
        verify(eventPublisher, never()).publish(any(), any());
    }
}
