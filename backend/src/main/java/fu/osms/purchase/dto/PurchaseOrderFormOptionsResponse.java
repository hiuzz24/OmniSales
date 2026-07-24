package fu.osms.purchase.dto;

import fu.osms.common.enums.PlatformType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class PurchaseOrderFormOptionsResponse {
    private String orderCode;
    private List<SupplierOption> suppliers;
    private WarehouseOption warehouse;
    private List<ProductGroupOption> productGroups;

    @Data
    @Builder
    public static class SupplierOption {
        private UUID id;
        private String code;
        private String name;
        private String contactName;
        private String phone;
    }

    @Data
    @Builder
    public static class WarehouseOption {
        private UUID id;
        private String name;
        private String address;
        private String shopifyLocationId;
        private String lazadaWarehouseCode;
        private String tiktokWarehouseId;
        private List<MarketplaceWarehouseOption> marketplaceWarehouses;
    }

    @Data
    @Builder
    public static class MarketplaceWarehouseOption {
        private UUID channelId;
        private String channelName;
        private PlatformType platform;
        private String externalWarehouseId;
        private String externalWarehouseKey;
    }

    @Data
    @Builder
    public static class ProductGroupOption {
        private String groupKey;
        private String sku;
        private String productName;
        private List<PlatformType> platforms;
        private List<ProductVariantOption> variants;
    }

    @Data
    @Builder
    public static class ProductVariantOption {
        private UUID variantId;
        private UUID productId;
        private String sku;
        private String marketplaceSku;
        private String productName;
        private String variantName;
        private BigDecimal price;
        private BigDecimal unitPrice;
        private BigDecimal averageCost;
        private List<MarketplaceVariantSource> marketplaceSources;
    }

    @Data
    @Builder
    public static class MarketplaceVariantSource {
        private UUID channelProductVariantId;
        private UUID channelId;
        private String channelName;
        private PlatformType platform;
        private String externalProductId;
        private String externalVariantId;
        private String externalSku;
        private String externalWarehouseId;
    }
}
