package fu.osms.inventory.dto.request;

import lombok.*;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ConfirmExtraItemsRequest {
    private List<ConfirmExtraItemRowDTO> rows;
}
