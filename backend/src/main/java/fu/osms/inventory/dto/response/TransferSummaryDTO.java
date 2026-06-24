package fu.osms.inventory.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferSummaryDTO {

    private long totalTickets;
    private long completedCount;
    private long inTransitCount;
    private long draftCount;
}
