package fu.osms.catalog.dto.request;

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
public class ChannelConfigRequest {

    private UUID channelId;
    private String categoryId;
    private String categoryName;
    private String categorySource;
    private Boolean categoryConfirmed;
    private String categoryVersion;
    private String brandId;
    private String brandName;
    private String sizeChartImageUrl;
    private Map<String, Object> attributes;
    private Map<String, String> variantAttributeBindings;
}
