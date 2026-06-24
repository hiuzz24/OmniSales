package fu.osms.inventory.dto.response;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferInventoryListResponse {
    private TransferSummaryDTO summary;
    private List<StockTransferResponseDTO> transfers;
    private long totalElements;
    private int totalPages;
}
