package fu.osms.order.repository.projection;

import java.math.BigDecimal;
import java.util.UUID;

public class CustomerOrderAggregate {

    private final UUID customerId;
    private final Long orderCount;
    private final BigDecimal totalSpent;

    public CustomerOrderAggregate(UUID customerId, Long orderCount, BigDecimal totalSpent) {
        this.customerId = customerId;
        this.orderCount = orderCount == null ? 0L : orderCount;
        this.totalSpent = totalSpent == null ? BigDecimal.ZERO : totalSpent;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public Long getOrderCount() {
        return orderCount;
    }

    public BigDecimal getTotalSpent() {
        return totalSpent;
    }
}
