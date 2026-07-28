package fu.osms.customer.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PageWithOrderCustomersResponse {

    private List<CustomerWithOrderSourceResponse> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;
    private AggregatedStats aggregated;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AggregatedStats {
        private Long totalOrders;
        private BigDecimal totalSpent;
    }
}
