package fu.osms.sync.service.impl;

import fu.osms.catalog.entity.ProductVariant;
import fu.osms.channel.entity.ChannelProductVariant;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.inventory.entity.InventoryItem;
import fu.osms.inventory.entity.Warehouse;
import fu.osms.inventory.repository.InventoryItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketplaceStockQuantityResolverImplTest {

    @Mock private ChannelProductVariantRepository channelProductVariantRepository;
    @Mock private InventoryItemRepository inventoryItemRepository;

    private MarketplaceStockQuantityResolverImpl resolver;

    @BeforeEach
    void setUp() {
        resolver = new MarketplaceStockQuantityResolverImpl(
                channelProductVariantRepository,
                inventoryItemRepository
        );
    }

    @Test
    void maxAvailableQuantityForSkuGroup_usesSharedAvailableAfterReserved() {
        UUID warehouseId = UUID.randomUUID();
        Warehouse warehouse = Warehouse.builder().id(warehouseId).build();
        ProductVariant shopifyVariant = ProductVariant.builder().id(UUID.randomUUID()).sku("SKU-SHOPIFY").build();
        ProductVariant tiktokVariant = ProductVariant.builder().id(UUID.randomUUID()).sku("SKU-TIKTOK").build();
        ChannelProductVariant mapping = ChannelProductVariant.builder()
                .variant(shopifyVariant)
                .externalSku("shared-sku")
                .build();
        ChannelProductVariant linkedMapping = ChannelProductVariant.builder()
                .variant(tiktokVariant)
                .externalSku("shared-sku")
                .build();

        when(channelProductVariantRepository.findActiveByNormalizedExternalSkuInWithVariant(List.of("shared-sku")))
                .thenReturn(List.of(mapping, linkedMapping));
        when(inventoryItemRepository.findByVariantIdIn(anyCollection()))
                .thenReturn(List.of(
                        InventoryItem.builder()
                                .id(UUID.randomUUID())
                                .warehouse(warehouse)
                                .variant(shopifyVariant)
                                .quantityOnHand(4)
                                .reservedQuantity(3)
                                .availableQuantity(1)
                                .build(),
                        InventoryItem.builder()
                                .id(UUID.randomUUID())
                                .warehouse(warehouse)
                                .variant(tiktokVariant)
                                .quantityOnHand(4)
                                .reservedQuantity(0)
                                .availableQuantity(4)
                                .build()
                ));

        int result = resolver.maxAvailableQuantityForSkuGroup(mapping);

        assertThat(result).isEqualTo(1);
    }
}
