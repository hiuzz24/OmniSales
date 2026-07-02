package fu.osms.sync.dto.shopify.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ShopifyProductRootResponse {
    @JsonProperty("product")
    private ShopifyProductResponse product;
}
