package fu.osms.sync.dto.shopify.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ShopifyProductPayload {
    private String title;
    @JsonProperty("body_html")
    private String bodyHtml;
    private String vendor;
    private String status;
    private String tags;
    private List<ShopifyVariantPayload> variants;
    private List<ShopifyOptionPayload> options;
    private List<ShopifyImagePayload> images;
}
