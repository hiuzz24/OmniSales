package fu.osms.sync.dto.shopify.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ShopifyVariantPayload {
    private Long id;
    private String sku;
    private String price;
    private String barcode;
    private String option1;
    private String option2;
    private String option3;
    @JsonProperty("grams")
    private Integer grams;
}
