package fu.osms.sync.dto.shopify;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class WebhookRegistrationResult {
    private String status;
    private String error;
    private List<Map<String, Object>> webhooks;
}
