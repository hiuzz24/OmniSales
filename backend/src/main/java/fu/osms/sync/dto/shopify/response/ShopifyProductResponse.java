package fu.osms.sync.dto.shopify.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class ShopifyProductResponse {
    private Long id;
    private List<ShopifyVariantResponse> variants;
}
