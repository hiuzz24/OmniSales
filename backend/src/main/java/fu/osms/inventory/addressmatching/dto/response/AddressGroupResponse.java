package fu.osms.inventory.addressmatching.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddressGroupResponse {
    private List<AddressGroup> groups;
    private int totalGroups;
    private boolean allSameAddress;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AddressGroup {
        private int groupId;
        private List<String> addresses;
        private boolean isSameAddress;
        private double averageScore;
    }
}
