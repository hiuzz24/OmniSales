package fu.osms.inventory.dto.response;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StockInImportResultDTO {
    private int successCount;
    private int skippedCount;
    private String errorFileUrl;
}
