package fu.osms.sync.shopify.impl;

import fu.osms.channel.dto.response.ChannelResponse;
import fu.osms.channel.service.ChannelService;
import fu.osms.sync.shopify.ShopifyApiClient;
import fu.osms.sync.shopify.ShopifyOAuthService;
import fu.osms.sync.shopify.ShopifyShopDomainNormalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShopifyChannelConnectionServiceImplTest {

    @Mock private ShopifyOAuthService shopifyOAuthService;
    @Mock private ShopifyApiClient shopifyApiClient;
    @Mock private ShopifyShopDomainNormalizer shopDomainNormalizer;
    @Mock private ChannelService channelService;

    private ShopifyChannelConnectionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ShopifyChannelConnectionServiceImpl(
                shopifyOAuthService,
                shopifyApiClient,
                shopDomainNormalizer,
                channelService
        );
    }

    @Test
    void buildAuthorizationUrl_normalizesShopBeforeBuildingUrl() {
        when(shopDomainNormalizer.normalizeHandle("demo")).thenReturn("demo.myshopify.com");
        when(shopifyOAuthService.buildAuthorizationUrl("demo.myshopify.com"))
                .thenReturn("https://demo.myshopify.com/oauth");

        assertThat(service.buildAuthorizationUrl("demo"))
                .isEqualTo("https://demo.myshopify.com/oauth");
    }

    @Test
    void connect_exchangesTokenChecksScopeConnectsAndRegistersWebhooks() {
        UUID channelId = UUID.randomUUID();
        ChannelResponse expected = ChannelResponse.builder().id(channelId).build();
        when(shopDomainNormalizer.normalizeHandle("demo")).thenReturn("demo.myshopify.com");
        when(shopifyOAuthService.exchangeCodeForToken("demo.myshopify.com", "code"))
                .thenReturn("access-token");
        when(shopifyApiClient.listAccessScopes("demo.myshopify.com", "access-token"))
                .thenReturn(List.of("read_products", "READ_LOCATIONS"));
        when(channelService.connectShopify("demo.myshopify.com", "access-token"))
                .thenReturn(expected);

        ChannelResponse actual = service.connect("demo", "code");

        assertThat(actual).isSameAs(expected);
        InOrder order = inOrder(shopifyOAuthService, shopifyApiClient, channelService);
        order.verify(shopifyOAuthService).exchangeCodeForToken("demo.myshopify.com", "code");
        order.verify(shopifyApiClient).listAccessScopes("demo.myshopify.com", "access-token");
        order.verify(channelService).connectShopify("demo.myshopify.com", "access-token");
        order.verify(channelService).registerShopifyWebhooks("demo.myshopify.com", "access-token", channelId);
    }

    @Test
    void connect_rejectsTokenWithoutReadLocationsScope() {
        when(shopDomainNormalizer.normalizeHandle("demo")).thenReturn("demo.myshopify.com");
        when(shopifyOAuthService.exchangeCodeForToken("demo.myshopify.com", "code"))
                .thenReturn("access-token");
        when(shopifyApiClient.listAccessScopes("demo.myshopify.com", "access-token"))
                .thenReturn(List.of("read_products"));

        assertThatThrownBy(() -> service.connect("demo", "code"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("read_locations");
        verify(channelService, never()).connectShopify("demo.myshopify.com", "access-token");
    }

    @Test
    void connect_doesNotRegisterWebhooksWhenChannelCreationFails() {
        when(shopDomainNormalizer.normalizeHandle("demo")).thenReturn("demo.myshopify.com");
        when(shopifyOAuthService.exchangeCodeForToken("demo.myshopify.com", "code"))
                .thenReturn("access-token");
        when(shopifyApiClient.listAccessScopes("demo.myshopify.com", "access-token"))
                .thenReturn(List.of("read_locations"));
        when(channelService.connectShopify("demo.myshopify.com", "access-token"))
                .thenThrow(new RuntimeException("Connect failed"));

        assertThatThrownBy(() -> service.connect("demo", "code"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Connect failed");
        verify(channelService, never()).registerShopifyWebhooks(anyString(), anyString(), any());
    }

    @Test
    void connect_propagatesOAuthFailure() {
        when(shopDomainNormalizer.normalizeHandle("demo")).thenReturn("demo.myshopify.com");
        when(shopifyOAuthService.exchangeCodeForToken("demo.myshopify.com", "code"))
                .thenThrow(new RuntimeException("OAuth failed"));

        assertThatThrownBy(() -> service.connect("demo", "code"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("OAuth failed");
        verify(shopifyApiClient, never()).listAccessScopes("demo.myshopify.com", "access-token");
    }
}
