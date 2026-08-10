package fu.osms.purchase.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderEvidenceRequest {

    @NotBlank(message = "Evidence URL is required")
    private String evidenceUrl;
}
