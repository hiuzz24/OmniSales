package fu.osms.inventory.addressmatching;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComponentMatchResult {
    private boolean matched;
    private double score;
    private String value1;
    private String value2;
}
