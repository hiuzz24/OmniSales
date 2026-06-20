package fu.osms.sync.dto.shopify.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ShopifyVariantResponse {
    private Long id;
    
    @JsonProperty("inventory_item_id")
    private Long inventoryItemId;
    
    private String sku;
}
