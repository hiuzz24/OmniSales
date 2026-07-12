package fu.osms.sync.lazada.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.catalog.repository.CategoryRepository;
import fu.osms.catalog.repository.ProductRepository;
import fu.osms.catalog.repository.ProductVariantRepository;
import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelCredential;
import fu.osms.channel.repository.ChannelCredentialRepository;
import fu.osms.channel.repository.ChannelProductRepository;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.exception.AppException;
import fu.osms.inventory.repository.InventoryItemRepository;
import fu.osms.inventory.repository.InventoryTransactionRepository;
import fu.osms.inventory.repository.WarehouseRepository;
import fu.osms.sync.entity.SyncLog;
import fu.osms.sync.lazada.service.LazadaApiClient;
import fu.osms.sync.repository.SyncLogRepository;
import fu.osms.sync.service.SyncAlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LazadaImportSyncServiceImplTest {

    @Mock private LazadaApiClient lazadaApiClient;
    @Mock private ChannelRepository channelRepository;
    @Mock private ChannelCredentialRepository credentialRepository;
    @Mock private ProductRepository productRepository;
    @Mock private ProductVariantRepository productVariantRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ChannelProductRepository channelProductRepository;
    @Mock private ChannelProductVariantRepository channelProductVariantRepository;
    @Mock private InventoryItemRepository inventoryItemRepository;
    @Mock private InventoryTransactionRepository inventoryTransactionRepository;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private SyncLogRepository syncLogRepository;
    @Mock private SyncAlertService syncAlertService;

    private LazadaImportSyncServiceImpl service;

    private Channel lazadaChannel;
    private ChannelCredential credential;

    @BeforeEach
    void setUp() {
        service = new LazadaImportSyncServiceImpl(
                lazadaApiClient, new ObjectMapper(),
                channelRepository, credentialRepository,
                productRepository, productVariantRepository, categoryRepository,
                channelProductRepository, channelProductVariantRepository,
                inventoryItemRepository, inventoryTransactionRepository, warehouseRepository,
                syncLogRepository, syncAlertService);

        lazadaChannel = Channel.builder()
                .id(UUID.randomUUID())
                .platform(PlatformType.LAZADA)
                .displayName("Lazada VN")
                .status("ACTIVE")
                .build();
        credential = ChannelCredential.builder()
                .id(UUID.randomUUID())
                .channel(lazadaChannel)
                .accessToken("acc-tok")
                .connectionState("CONNECTED")
                .tokenExpiresAt(OffsetDateTime.now().plusHours(1))
                .build();
    }

    private void stubSyncLogSave() {
        when(syncLogRepository.save(any(SyncLog.class))).thenAnswer(i -> {
            SyncLog log = i.getArgument(0);
            if (log.getId() == null) log.setId(UUID.randomUUID());
            return log;
        });
    }

    @Test
    @DisplayName("syncProductsAndWarehouses — channel not found → AppException")
    void sync_channelNotFound() {
        UUID fake = UUID.randomUUID();
        when(channelRepository.findById(fake)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.syncProductsAndWarehouses(fake))
                .isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("syncProductsAndWarehouses — channel deleted → AppException")
    void sync_channelDeleted() {
        lazadaChannel.setDeletedAt(OffsetDateTime.now());
        when(channelRepository.findById(lazadaChannel.getId())).thenReturn(Optional.of(lazadaChannel));

        assertThatThrownBy(() -> service.syncProductsAndWarehouses(lazadaChannel.getId()))
                .isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("syncProductsAndWarehouses — wrong platform → IllegalArgumentException")
    void sync_wrongPlatform() {
        lazadaChannel.setPlatform(PlatformType.SHOPIFY);
        when(channelRepository.findById(lazadaChannel.getId())).thenReturn(Optional.of(lazadaChannel));

        assertThatThrownBy(() -> service.syncProductsAndWarehouses(lazadaChannel.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Lazada");
    }

    @Test
    @DisplayName("syncProductsAndWarehouses — no credential → IllegalStateException")
    void sync_noCredential() {
        when(channelRepository.findById(lazadaChannel.getId())).thenReturn(Optional.of(lazadaChannel));
        when(credentialRepository.findByChannelIdAndConnectionState(lazadaChannel.getId(), "CONNECTED"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.syncProductsAndWarehouses(lazadaChannel.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("token");
    }

    @Test
    @DisplayName("syncProductsAndWarehouses — blank access_token → IllegalStateException")
    void sync_blankToken() {
        credential.setAccessToken("  ");
        when(channelRepository.findById(lazadaChannel.getId())).thenReturn(Optional.of(lazadaChannel));
        when(credentialRepository.findByChannelIdAndConnectionState(lazadaChannel.getId(), "CONNECTED"))
                .thenReturn(Optional.of(credential));

        assertThatThrownBy(() -> service.syncProductsAndWarehouses(lazadaChannel.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("access_token");
    }
}
