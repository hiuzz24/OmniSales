package fu.osms.sync.dto.shopify.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ShopifyImagePayload {
    private String src;
    private Integer position;
}
