package fu.osms.reporting.service.impl;

import fu.osms.order.repository.OrderRepository;
import fu.osms.reporting.dto.ChannelOrderReportResponse;
import fu.osms.reporting.repository.projection.ChannelOrderAggregateProjection;
import fu.osms.reporting.repository.projection.OrderStatusAggregateProjection;
import fu.osms.reporting.service.ChannelOrderReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChannelOrderReportServiceImpl implements ChannelOrderReportService {

    private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int DEFAULT_RANGE_DAYS = 29;

    private final OrderRepository orderRepository;

    @Override
    public ChannelOrderReportResponse getReport(LocalDate from, LocalDate to) {
        LocalDate resolvedTo = to != null ? to : LocalDate.now(REPORT_ZONE);
        LocalDate resolvedFrom = from != null ? from : resolvedTo.minusDays(DEFAULT_RANGE_DAYS);
        if (resolvedFrom.isAfter(resolvedTo)) {
            throw new ResponseStatusException(BAD_REQUEST, "Ngày bắt đầu không được sau ngày kết thúc");
        }

        OffsetDateTime fromDateTime = resolvedFrom.atStartOfDay(REPORT_ZONE).toOffsetDateTime();
        OffsetDateTime toExclusive = resolvedTo.plusDays(1).atStartOfDay(REPORT_ZONE).toOffsetDateTime();

        List<ChannelOrderAggregateProjection> aggregates =
                orderRepository.aggregateOrdersByChannel(fromDateTime, toExclusive);
        List<OrderStatusAggregateProjection> statusAggregates =
                orderRepository.aggregateOrderStatuses(fromDateTime, toExclusive);

        long totalOrders = aggregates.stream().mapToLong(this::orderCount).sum();
        long validOrders = aggregates.stream().mapToLong(this::validOrderCount).sum();
        long deliveredOrders = aggregates.stream().mapToLong(this::deliveredCount).sum();
        BigDecimal totalRevenue = aggregates.stream()
                .map(row -> money(row.getRevenue()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<ChannelOrderReportResponse.ChannelMetric> channels = aggregates.stream()
                .map(row -> ChannelOrderReportResponse.ChannelMetric.builder()
                        .channelId(row.getChannelId())
                        .platform(row.getPlatform())
                        .channelName(row.getChannelName())
                        .orderCount(orderCount(row))
                        .revenue(money(row.getRevenue()))
                        .averageOrderValue(average(money(row.getRevenue()), validOrderCount(row)))
                        .deliveredCount(deliveredCount(row))
                        .cancelledCount(count(row.getCancelledCount()))
                        .orderShare(percent(orderCount(row), totalOrders))
                        .build())
                .toList();

        List<ChannelOrderReportResponse.StatusMetric> statuses = statusAggregates.stream()
                .map(row -> ChannelOrderReportResponse.StatusMetric.builder()
                        .status(row.getStatus())
                        .orderCount(count(row.getOrderCount()))
                        .build())
                .toList();

        return ChannelOrderReportResponse.builder()
                .fromDate(resolvedFrom)
                .toDate(resolvedTo)
                .totalOrders(totalOrders)
                .totalRevenue(totalRevenue)
                .averageOrderValue(average(totalRevenue, validOrders))
                .deliveredOrders(deliveredOrders)
                .deliveredRate(percent(deliveredOrders, totalOrders))
                .channels(channels)
                .statuses(statuses)
                .build();
    }

    private long orderCount(ChannelOrderAggregateProjection row) {
        return count(row.getOrderCount());
    }

    private long validOrderCount(ChannelOrderAggregateProjection row) {
        return count(row.getValidOrderCount());
    }

    private long deliveredCount(ChannelOrderAggregateProjection row) {
        return count(row.getDeliveredCount());
    }

    private long count(Long value) {
        return value == null ? 0L : value;
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal average(BigDecimal value, long divisor) {
        return divisor == 0 ? BigDecimal.ZERO : value.divide(BigDecimal.valueOf(divisor), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal percent(long value, long total) {
        return total == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(value)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
    }
}
