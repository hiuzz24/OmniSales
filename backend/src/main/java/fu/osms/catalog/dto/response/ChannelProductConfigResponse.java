package fu.osms.catalog.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChannelProductConfigResponse {
    private UUID channelId;
    private String channelName;
    private String platform;
    private String categoryId;
    private String categoryName;
    private String categorySource;
    private Boolean categoryConfirmed;
    private String categoryVersion;
    private String brandId;
    private String brandName;
    private Map<String, Object> attributes;
    private Map<String, String> variantAttributeBindings;
    private Boolean readyToSync;
    private String configurationError;
}
