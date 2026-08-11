package fu.osms.reporting.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class ProductReportResponse {
    private LocalDate fromDate;
    private LocalDate toDate;
    private long activeProducts;
    private long totalUnitsSold;
    private BigDecimal unitsGrowthRate;
    private BigDecimal totalRevenue;
    private BigDecimal revenueGrowthRate;
    private GrowthProduct topGrowthProduct;
    private List<ProductMetric> products;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class GrowthProduct {
        private UUID productId;
        private UUID variantId;
        private String sku;
        private String productName;
        private BigDecimal growthRate;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class ProductMetric {
        private UUID productId;
        private UUID variantId;
        private String sku;
        private String productName;
        private String variantName;
        private long unitsSold;
        private long returnedUnits;
        private BigDecimal returnRate;
        private BigDecimal revenue;
        private long previousUnitsSold;
        private BigDecimal growthRate;
    }
}
