package fu.osms.sync.lazada.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelCredential;
import fu.osms.channel.repository.ChannelCredentialRepository;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.channel.token.dto.AccessTokenContext;
import fu.osms.channel.token.service.ChannelTokenService;
import fu.osms.common.enums.PlatformType;
import fu.osms.inventory.repository.InventoryIssueRepository;
import fu.osms.inventory.repository.InventoryItemRepository;
import fu.osms.inventory.repository.StockReceiveRepository;
import fu.osms.sync.lazada.dto.LazadaInventorySyncResult;
import fu.osms.sync.lazada.inventory.LazadaInventoryGateway;
import fu.osms.sync.lazada.service.LazadaAuthorizedApiClient;
import fu.osms.sync.service.MarketplaceStockQuantityResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LazadaInventoryUpdateServiceImplTest {

    @Mock private LazadaAuthorizedApiClient lazadaApiClient;
    @Mock private ChannelTokenService channelTokenService;
    @Mock private ChannelCredentialRepository credentialRepository;
    @Mock private ChannelProductVariantRepository channelProductVariantRepository;
    @Mock private InventoryItemRepository inventoryItemRepository;
    @Mock private StockReceiveRepository stockReceiveRepository;
    @Mock private InventoryIssueRepository inventoryIssueRepository;
    @Mock private MarketplaceStockQuantityResolver marketplaceStockQuantityResolver;
    @Mock private LazadaInventoryGateway lazadaInventoryGateway;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private LazadaInventoryUpdateServiceImpl service;

    private UUID channelId;
    private Channel channel;

    @BeforeEach
    void setUp() {
        service = new LazadaInventoryUpdateServiceImpl(
                lazadaApiClient,
                channelTokenService,
                objectMapper,
                credentialRepository,
                channelProductVariantRepository,
                inventoryItemRepository,
                stockReceiveRepository,
                inventoryIssueRepository,
                marketplaceStockQuantityResolver,
                lazadaInventoryGateway
        );

        channelId = UUID.randomUUID();
        channel = Channel.builder()
                .id(channelId)
                .platform(PlatformType.LAZADA)
                .displayName("Lazada-Test")
                .metadata(new HashMap<>())
                .build();
    }

    @Test
    @DisplayName("Should throw when no credential found")
    void shouldThrowWhenNoCredential() {
        when(credentialRepository.findByChannelIdAndConnectionState(channelId, "CONNECTED"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.syncChangedSellableStock(channelId, null, null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("token");
    }

    @Test
    @DisplayName("Should return zero when no mappings found")
    void shouldReturnZeroWhenNoMappings() {
        ChannelCredential credential = ChannelCredential.builder()
                .id(UUID.randomUUID())
                .channel(channel)
                .accessToken("access-tok")
                .connectionState("CONNECTED")
                .build();

        when(credentialRepository.findByChannelIdAndConnectionState(channelId, "CONNECTED"))
                .thenReturn(Optional.of(credential));
        when(channelTokenService.getValidToken(channelId))
                .thenReturn(new AccessTokenContext(channelId, PlatformType.LAZADA, "access-tok", OffsetDateTime.now().plusSeconds(3600)));
        when(channelProductVariantRepository.findActiveByChannelIdWithVariant(channelId))
                .thenReturn(List.of());

        LazadaInventorySyncResult result = service.syncChangedSellableStock(channelId, null, null, null);

        assertThat(result.pushedVariantCount()).isZero();
        verify(lazadaApiClient, never()).executePost(any(UUID.class), any(String.class), anyMap());
    }

    @Test
    @DisplayName("Should skip sync when changedSince with empty variants")
    void shouldSkipWhenChangedSinceWithEmptyVariants() {
        ChannelCredential credential = ChannelCredential.builder()
                .id(UUID.randomUUID())
                .channel(channel)
                .accessToken("access-tok")
                .connectionState("CONNECTED")
                .build();

        when(credentialRepository.findByChannelIdAndConnectionState(channelId, "CONNECTED"))
                .thenReturn(Optional.of(credential));
        when(channelTokenService.getValidToken(channelId))
                .thenReturn(new AccessTokenContext(channelId, PlatformType.LAZADA, "access-tok", OffsetDateTime.now().plusSeconds(3600)));

        LazadaInventorySyncResult result = service.syncChangedSellableStock(
                channelId,
                OffsetDateTime.now().minusHours(1),
                OffsetDateTime.now(),
                Set.of()
        );

        assertThat(result.pushedVariantCount()).isZero();
    }
}
