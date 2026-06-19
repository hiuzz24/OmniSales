package fu.osms.customer.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerStatsResponse {
    private long totalCustomers;
    private long activeCustomers;
    private long totalOrders;
    private BigDecimal totalSpent;
}
