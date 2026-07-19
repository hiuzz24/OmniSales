package fu.osms.catalog.service.impl;

import fu.osms.catalog.dto.response.ProductInsightsResponse;
import fu.osms.catalog.repository.ProductRepository;
import fu.osms.catalog.repository.projection.ProductSalesAggregate;
import fu.osms.catalog.repository.projection.ProductWarehouseInventoryAggregate;
import fu.osms.catalog.service.ProductInsightsService;
import fu.osms.inventory.repository.InventoryItemRepository;
import fu.osms.order.repository.OrderItemRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductInsightsServiceImpl implements ProductInsightsService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String CURRENCY = "VND";

    private final ProductRepository productRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final OrderItemRepository orderItemRepository;

    @Override
    @Transactional(readOnly = true)
    public ProductInsightsResponse getInsights(UUID productId) {
        if (productRepository.findByIdAndDeletedAtIsNull(productId).isEmpty()) {
            throw new EntityNotFoundException("Product not found: " + productId);
        }

        List<ProductWarehouseInventoryAggregate> warehouseRows =
                inventoryItemRepository.aggregateByProductAndWarehouse(productId);
        List<ProductInsightsResponse.WarehouseSummary> warehouses = warehouseRows.stream()
                .map(this::toWarehouseSummary)
                .toList();

        long quantityOnHand = warehouses.stream().mapToLong(ProductInsightsResponse.WarehouseSummary::quantityOnHand).sum();
        long reservedQuantity = warehouses.stream().mapToLong(ProductInsightsResponse.WarehouseSummary::reservedQuantity).sum();
        long availableQuantity = warehouses.stream().mapToLong(ProductInsightsResponse.WarehouseSummary::availableQuantity).sum();
        boolean lowStock = warehouses.stream().anyMatch(ProductInsightsResponse.WarehouseSummary::lowStock);

        ProductSalesAggregate allTime = orderItemRepository.aggregateDeliveredSalesByProduct(productId);
        ZonedDateTime currentMonthStart = ZonedDateTime.now(BUSINESS_ZONE)
                .withDayOfMonth(1)
                .toLocalDate()
                .atStartOfDay(BUSINESS_ZONE);
        OffsetDateTime currentStart = currentMonthStart.toOffsetDateTime();
        OffsetDateTime nextStart = currentMonthStart.plusMonths(1).toOffsetDateTime();
        OffsetDateTime previousStart = currentMonthStart.minusMonths(1).toOffsetDateTime();

        ProductSalesAggregate currentMonth = orderItemRepository.aggregateDeliveredSalesByProductBetween(
                productId, currentStart, nextStart);
        ProductSalesAggregate previousMonth = orderItemRepository.aggregateDeliveredSalesByProductBetween(
                productId, previousStart, currentStart);

        SalesValues allTimeValues = salesValues(allTime);
        SalesValues currentValues = salesValues(currentMonth);
        SalesValues previousValues = salesValues(previousMonth);

        ProductInsightsResponse.SalesSummary sales = new ProductInsightsResponse.SalesSummary(
                allTimeValues.unitsSold(),
                allTimeValues.revenue(),
                allTimeValues.grossProfit(),
                margin(allTimeValues.grossProfit(), allTimeValues.revenue()),
                CURRENCY,
                allTimeValues.costDataComplete()
        );

        ProductInsightsResponse.MonthlyComparison comparison = new ProductInsightsResponse.MonthlyComparison(
                period(currentValues),
                period(previousValues),
                changePercent(BigDecimal.valueOf(currentValues.unitsSold()), BigDecimal.valueOf(previousValues.unitsSold())),
                changePercent(currentValues.revenue(), previousValues.revenue()),
                currentValues.grossProfit() == null || previousValues.grossProfit() == null
                        ? null
                        : changePercent(currentValues.grossProfit(), previousValues.grossProfit())
        );

        return new ProductInsightsResponse(
                new ProductInsightsResponse.InventorySummary(
                        quantityOnHand, reservedQuantity, availableQuantity, lowStock),
                sales,
                comparison,
                warehouses
        );
    }

    private ProductInsightsResponse.WarehouseSummary toWarehouseSummary(ProductWarehouseInventoryAggregate row) {
        return new ProductInsightsResponse.WarehouseSummary(
                row.getWarehouseId(),
                row.getWarehouseName(),
                longValue(row.getQuantityOnHand()),
                longValue(row.getReservedQuantity()),
                longValue(row.getAvailableQuantity()),
                longValue(row.getLowStockCount()) > 0
        );
    }

    private SalesValues salesValues(ProductSalesAggregate row) {
        long unitsSold = row == null ? 0 : longValue(row.getUnitsSold());
        BigDecimal revenue = row == null ? BigDecimal.ZERO : decimal(row.getRevenue());
        BigDecimal resolvedCost = row == null ? BigDecimal.ZERO : decimal(row.getResolvedCost());
        long fallbackCount = row == null ? 0 : longValue(row.getFallbackCostCount());
        long missingCount = row == null ? 0 : longValue(row.getMissingCostCount());
        BigDecimal grossProfit = missingCount > 0 ? null : revenue.subtract(resolvedCost);
        return new SalesValues(unitsSold, revenue, grossProfit, fallbackCount == 0 && missingCount == 0);
    }

    private ProductInsightsResponse.PeriodSummary period(SalesValues values) {
        return new ProductInsightsResponse.PeriodSummary(
                values.unitsSold(), values.revenue(), values.grossProfit());
    }

    private BigDecimal margin(BigDecimal grossProfit, BigDecimal revenue) {
        if (grossProfit == null) {
            return null;
        }
        if (revenue.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return grossProfit.multiply(BigDecimal.valueOf(100))
                .divide(revenue, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal changePercent(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.signum() == 0) {
            return null;
        }
        return current.subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(previous.abs(), 2, RoundingMode.HALF_UP);
    }

    private long longValue(Long value) {
        return value == null ? 0 : value;
    }

    private BigDecimal decimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private record SalesValues(
            long unitsSold,
            BigDecimal revenue,
            BigDecimal grossProfit,
            boolean costDataComplete
    ) {
    }
}
