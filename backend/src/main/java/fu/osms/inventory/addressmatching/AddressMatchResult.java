package fu.osms.inventory.addressmatching;

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
public class AddressMatchResult {
    private boolean matched;
    private double score;
    private MatchLevel level;
    private String normalizedAddress1;
    private String normalizedAddress2;
    private AddressComponents components1;
    private AddressComponents components2;
    private Map<String, ComponentMatchResult> componentResults;
    private List<String> conflicts;
}
