package fu.osms.sync.dto.shopify.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ShopifyWebhookResponse {
    private Long id;
    private String topic;
    private String address;
    private String format;

    @JsonProperty("created_at")
    private String createdAt;
}
