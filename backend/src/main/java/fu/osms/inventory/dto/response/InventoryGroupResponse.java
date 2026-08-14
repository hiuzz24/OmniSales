package fu.osms.inventory.dto.response;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryGroupResponse {

    private String groupKey;
    private InventoryItemResponse parent;
    private List<InventoryItemResponse> children;
}
