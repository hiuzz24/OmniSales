package fu.osms.reporting.service.impl;

import fu.osms.catalog.enums.ProductStatus;
import fu.osms.catalog.repository.ProductRepository;
import fu.osms.catalog.repository.ProductVariantRepository;
import fu.osms.order.repository.OrderItemRepository;
import fu.osms.orderreturn.repository.OrderReturnItemRepository;
import fu.osms.reporting.dto.ProductReportResponse;
import fu.osms.reporting.repository.projection.ProductCatalogProjection;
import fu.osms.reporting.repository.projection.ProductReturnProjection;
import fu.osms.reporting.repository.projection.ProductSalesProjection;
import fu.osms.reporting.service.ProductReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductReportServiceImpl implements ProductReportService {

    private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int DEFAULT_RANGE_DAYS = 29;

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderReturnItemRepository orderReturnItemRepository;

    @Override
    public ProductReportResponse getReport(LocalDate from, LocalDate to) {
        LocalDate resolvedTo = to != null ? to : LocalDate.now(REPORT_ZONE);
        LocalDate resolvedFrom = from != null ? from : resolvedTo.minusDays(DEFAULT_RANGE_DAYS);
        if (resolvedFrom.isAfter(resolvedTo)) {
            throw new ResponseStatusException(BAD_REQUEST, "Ngày bắt đầu không được sau ngày kết thúc");
        }

        long rangeDays = ChronoUnit.DAYS.between(resolvedFrom, resolvedTo) + 1;
        LocalDate previousTo = resolvedFrom.minusDays(1);
        LocalDate previousFrom = previousTo.minusDays(rangeDays - 1);
        OffsetDateTime fromDateTime = startOfDay(resolvedFrom);
        OffsetDateTime toExclusive = startOfDay(resolvedTo.plusDays(1));
        OffsetDateTime previousFromDateTime = startOfDay(previousFrom);
        OffsetDateTime previousToExclusive = startOfDay(previousTo.plusDays(1));

        List<ProductCatalogProjection> catalog = productVariantRepository.findActiveProductReportCatalog();
        List<ProductSalesProjection> currentSales = orderItemRepository.aggregateProductSales(fromDateTime, toExclusive);
        List<ProductSalesProjection> previousSales = orderItemRepository.aggregateProductSales(previousFromDateTime, previousToExclusive);
        List<ProductReturnProjection> returns = orderReturnItemRepository.aggregateProductReturns(fromDateTime, toExclusive);

        Map<String, MutableMetric> metrics = new LinkedHashMap<>();
        catalog.forEach(row -> metrics.put(key(row.getVariantId(), row.getSku()), new MutableMetric(
                row.getProductId(), row.getVariantId(), row.getSku(), row.getProductName(), row.getVariantName())));
        currentSales.forEach(row -> {
            MutableMetric metric = metrics.computeIfAbsent(key(row.getVariantId(), row.getSku()), ignored ->
                    new MutableMetric(row.getProductId(), row.getVariantId(), row.getSku(), row.getProductName(), row.getVariantName()));
            metric.unitsSold += count(row.getUnitsSold());
            metric.revenue = metric.revenue.add(money(row.getRevenue()));
        });
        previousSales.forEach(row -> metrics.computeIfAbsent(key(row.getVariantId(), row.getSku()), ignored ->
                        new MutableMetric(row.getProductId(), row.getVariantId(), row.getSku(), row.getProductName(), row.getVariantName()))
                .previousUnitsSold += count(row.getUnitsSold()));
        returns.forEach(row -> metrics.computeIfAbsent(key(row.getVariantId(), row.getSku()), ignored ->
                        new MutableMetric(null, row.getVariantId(), row.getSku(), row.getSku(), null))
                .returnedUnits += count(row.getReturnedUnits()));

        List<ProductReportResponse.ProductMetric> products = metrics.values().stream()
                .map(this::toMetric)
                .sorted(Comparator.comparingLong(ProductReportResponse.ProductMetric::getUnitsSold).reversed()
                        .thenComparing(ProductReportResponse.ProductMetric::getRevenue, Comparator.reverseOrder()))
                .toList();
        long totalUnitsSold = currentSales.stream().mapToLong(row -> count(row.getUnitsSold())).sum();
        long previousUnitsSold = previousSales.stream().mapToLong(row -> count(row.getUnitsSold())).sum();
        BigDecimal totalRevenue = currentSales.stream().map(row -> money(row.getRevenue())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal previousRevenue = previousSales.stream().map(row -> money(row.getRevenue())).reduce(BigDecimal.ZERO, BigDecimal::add);
        ProductReportResponse.ProductMetric growth = products.stream().filter(row -> row.getUnitsSold() > 0)
                .max(Comparator.comparing(ProductReportResponse.ProductMetric::getGrowthRate)).orElse(null);

        return ProductReportResponse.builder()
                .fromDate(resolvedFrom)
                .toDate(resolvedTo)
                .activeProducts(productRepository.countByStatusAndDeletedAtIsNull(ProductStatus.ACTIVE))
                .totalUnitsSold(totalUnitsSold)
                .unitsGrowthRate(growthRate(totalUnitsSold, previousUnitsSold))
                .totalRevenue(totalRevenue)
                .revenueGrowthRate(growthRate(totalRevenue, previousRevenue))
                .topGrowthProduct(growth == null ? null : ProductReportResponse.GrowthProduct.builder()
                        .productId(growth.getProductId()).variantId(growth.getVariantId()).sku(growth.getSku())
                        .productName(growth.getProductName()).growthRate(growth.getGrowthRate()).build())
                .products(products)
                .build();
    }

    private ProductReportResponse.ProductMetric toMetric(MutableMetric metric) {
        return ProductReportResponse.ProductMetric.builder()
                .productId(metric.productId).variantId(metric.variantId).sku(metric.sku)
                .productName(metric.productName).variantName(metric.variantName)
                .unitsSold(metric.unitsSold).returnedUnits(metric.returnedUnits)
                .returnRate(percent(metric.returnedUnits, metric.unitsSold))
                .revenue(metric.revenue).previousUnitsSold(metric.previousUnitsSold)
                .growthRate(growthRate(metric.unitsSold, metric.previousUnitsSold)).build();
    }

    private OffsetDateTime startOfDay(LocalDate date) { return date.atStartOfDay(REPORT_ZONE).toOffsetDateTime(); }
    private String key(UUID variantId, String sku) {
        return variantId != null ? "variant:" + variantId : "sku:" + (sku == null ? "unknown" : sku.trim().toLowerCase(Locale.ROOT));
    }
    private long count(Long value) { return value == null ? 0L : value; }
    private BigDecimal money(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private BigDecimal percent(long value, long total) {
        return total == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(value).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
    }
    private BigDecimal growthRate(long current, long previous) {
        if (previous == 0) return current == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(100);
        return BigDecimal.valueOf(current - previous).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(previous), 1, RoundingMode.HALF_UP);
    }
    private BigDecimal growthRate(BigDecimal current, BigDecimal previous) {
        if (previous.signum() == 0) return current.signum() == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(100);
        return current.subtract(previous).multiply(BigDecimal.valueOf(100)).divide(previous, 1, RoundingMode.HALF_UP);
    }

    private static class MutableMetric {
        private final UUID productId; private final UUID variantId; private final String sku;
        private final String productName; private final String variantName;
        private long unitsSold; private long returnedUnits; private long previousUnitsSold;
        private BigDecimal revenue = BigDecimal.ZERO;
        private MutableMetric(UUID productId, UUID variantId, String sku, String productName, String variantName) {
            this.productId = productId; this.variantId = variantId; this.sku = sku;
            this.productName = productName == null || productName.isBlank() ? "Sản phẩm chưa xác định" : productName;
            this.variantName = variantName;
        }
    }
}
