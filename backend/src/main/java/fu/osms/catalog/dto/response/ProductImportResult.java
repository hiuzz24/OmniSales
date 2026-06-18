package fu.osms.catalog.dto.response;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductImportResult {

    private int totalRows;

    private int createdCount;

    private int updatedCount;

    @Builder.Default
    private List<String> createdSkus = new ArrayList<>();

    @Builder.Default
    private List<String> updatedSkus = new ArrayList<>();

    @Builder.Default
    private List<String> errors = new ArrayList<>();
}
