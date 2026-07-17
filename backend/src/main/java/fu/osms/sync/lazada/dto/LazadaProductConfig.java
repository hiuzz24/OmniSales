package fu.osms.sync.lazada.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LazadaProductConfig {
    private String categoryId;
    private String categoryName;
    private String brandId;
    private String brandName;
    private Map<String, Object> attributes;
    private Map<String, String> variantAttributeBindings;
    private Boolean readyToSync;
    private String configurationError;
}
