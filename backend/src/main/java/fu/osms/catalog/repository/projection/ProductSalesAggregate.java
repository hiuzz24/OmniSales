package fu.osms.catalog.repository.projection;

import java.math.BigDecimal;

public interface ProductSalesAggregate {
    Long getUnitsSold();

    BigDecimal getRevenue();

    BigDecimal getResolvedCost();

    Long getFallbackCostCount();

    Long getMissingCostCount();
}
