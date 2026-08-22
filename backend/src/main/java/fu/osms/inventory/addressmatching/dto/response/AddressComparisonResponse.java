package fu.osms.inventory.addressmatching.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddressComparisonResponse {
    private boolean matched;
    private double score;
    private String level;
    private String normalizedAddress1;
    private String normalizedAddress2;
    private Map<String, ComponentResultResponse> components;
    private List<String> conflicts;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ComponentResultResponse {
        private boolean matched;
        private double score;
    }
}
