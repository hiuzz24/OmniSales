package fu.osms.catalog.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ProductInsightsResponse(
        InventorySummary inventory,
        SalesSummary sales,
        MonthlyComparison monthlyComparison,
        List<WarehouseSummary> warehouses
) {
    public record InventorySummary(
            long quantityOnHand,
            long reservedQuantity,
            long availableQuantity,
            boolean lowStock
    ) {
    }

    public record SalesSummary(
            long unitsSold,
            BigDecimal revenue,
            BigDecimal grossProfit,
            BigDecimal marginPercent,
            String currency,
            boolean costDataComplete
    ) {
    }

    public record PeriodSummary(
            long unitsSold,
            BigDecimal revenue,
            BigDecimal grossProfit
    ) {
    }

    public record MonthlyComparison(
            PeriodSummary currentMonth,
            PeriodSummary previousMonth,
            BigDecimal unitsSoldChangePercent,
            BigDecimal revenueChangePercent,
            BigDecimal grossProfitChangePercent
    ) {
    }

    public record WarehouseSummary(
            UUID warehouseId,
            String warehouseName,
            long quantityOnHand,
            long reservedQuantity,
            long availableQuantity,
            boolean lowStock
    ) {
    }
}
