package fu.osms.reporting.service.impl;

import fu.osms.order.enums.OrderStatus;
import fu.osms.order.repository.OrderRepository;
import fu.osms.orderreturn.enums.OrderReturnStatus;
import fu.osms.orderreturn.repository.OrderReturnItemRepository;
import fu.osms.orderreturn.repository.OrderReturnRepository;
import fu.osms.reporting.dto.ReturnReportResponse;
import fu.osms.reporting.repository.projection.ReturnReportItemProjection;
import fu.osms.reporting.repository.projection.ReturnReportRowProjection;
import fu.osms.reporting.service.ReturnReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReturnReportServiceImpl implements ReturnReportService {

    private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int DEFAULT_RANGE_DAYS = 29;
    private static final String UNKNOWN_REASON = "Không xác định";

    private final OrderReturnRepository orderReturnRepository;
    private final OrderReturnItemRepository orderReturnItemRepository;
    private final OrderRepository orderRepository;

    @Override
    public ReturnReportResponse getReport(LocalDate from, LocalDate to) {
        LocalDate resolvedTo = to != null ? to : LocalDate.now(REPORT_ZONE);
        LocalDate resolvedFrom = from != null ? from : resolvedTo.minusDays(DEFAULT_RANGE_DAYS);
        if (resolvedFrom.isAfter(resolvedTo)) {
            throw new ResponseStatusException(BAD_REQUEST, "Ngày bắt đầu không được sau ngày kết thúc");
        }

        OffsetDateTime fromDateTime = resolvedFrom.atStartOfDay(REPORT_ZONE).toOffsetDateTime();
        OffsetDateTime toExclusive = resolvedTo.plusDays(1).atStartOfDay(REPORT_ZONE).toOffsetDateTime();
        List<ReturnReportRowProjection> rows = orderReturnRepository.findReportRows(fromDateTime, toExclusive);
        List<UUID> returnIds = rows.stream().map(ReturnReportRowProjection::getReturnId).toList();
        Map<UUID, List<ReturnReportResponse.ReturnItem>> itemsByReturn = loadItems(returnIds);

        long totalRequests = rows.size();
        long approvedRequests = rows.stream().filter(row -> isApproved(row.getStatus())).count();
        BigDecimal totalReturnValue = rows.stream()
                .map(row -> returnValue(itemsByReturn.getOrDefault(row.getReturnId(), List.of())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long deliveredOrders = orderRepository.countByStatusInDateRange(
                OrderStatus.DELIVERED, fromDateTime, toExclusive);

        Map<String, Long> reasonCounts = new LinkedHashMap<>();
        rows.forEach(row -> reasonCounts.merge(reason(row.getMetadata()), 1L, Long::sum));
        List<ReturnReportResponse.ReasonMetric> reasons = reasonCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> ReturnReportResponse.ReasonMetric.builder()
                        .reason(entry.getKey())
                        .count(entry.getValue())
                        .percentage(percent(entry.getValue(), totalRequests))
                        .build())
                .toList();

        Map<String, CustomerAccumulator> grouped = new LinkedHashMap<>();
        for (ReturnReportRowProjection row : rows) {
            String key = customerKey(row);
            CustomerAccumulator customer = grouped.computeIfAbsent(key, ignored -> new CustomerAccumulator(
                    key,
                    row.getCustomerId(),
                    customerName(row),
                    firstText(row.getCustomerPhone(), row.getBuyerPhone()),
                    new ArrayList<>()));
            List<ReturnReportResponse.ReturnItem> returnItems = itemsByReturn.getOrDefault(row.getReturnId(), List.of());
            customer.returns.add(ReturnReportResponse.ReturnDetail.builder()
                    .returnId(row.getReturnId())
                    .orderId(row.getOrderId())
                    .externalOrderId(row.getExternalOrderId())
                    .externalReturnId(row.getExternalReturnId())
                    .platform(row.getPlatform())
                    .channelName(row.getChannelName())
                    .status(row.getStatus())
                    .value(returnValue(returnItems))
                    .reason(reason(row.getMetadata()))
                    .createdAt(row.getCreatedAt())
                    .items(returnItems)
                    .build());
        }

        List<ReturnReportResponse.CustomerMetric> customers = grouped.values().stream()
                .map(this::toCustomerMetric)
                .sorted(Comparator.comparingLong(ReturnReportResponse.CustomerMetric::getReturnCount).reversed()
                        .thenComparing(ReturnReportResponse.CustomerMetric::getLastReturnAt,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        return ReturnReportResponse.builder()
                .fromDate(resolvedFrom)
                .toDate(resolvedTo)
                .totalRequests(totalRequests)
                .returnRate(percent(totalRequests, deliveredOrders))
                .totalReturnValue(totalReturnValue)
                .approvedRequests(approvedRequests)
                .reasons(reasons)
                .customers(customers)
                .build();
    }

    private Map<UUID, List<ReturnReportResponse.ReturnItem>> loadItems(List<UUID> returnIds) {
        if (returnIds.isEmpty()) return Map.of();
        Map<UUID, List<ReturnReportResponse.ReturnItem>> result = new LinkedHashMap<>();
        for (ReturnReportItemProjection item : orderReturnItemRepository.findReportItems(returnIds)) {
            result.computeIfAbsent(item.getReturnId(), ignored -> new ArrayList<>())
                    .add(ReturnReportResponse.ReturnItem.builder()
                            .sku(item.getSku())
                            .name(item.getName())
                            .quantity(item.getQuantity() == null ? 0 : item.getQuantity())
                            .unitPrice(money(item.getUnitPrice()))
                            .build());
        }
        return result;
    }

    private ReturnReportResponse.CustomerMetric toCustomerMetric(CustomerAccumulator accumulator) {
        List<ReturnReportResponse.ReturnDetail> returns = accumulator.returns.stream()
                .sorted(Comparator.comparing(ReturnReportResponse.ReturnDetail::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        BigDecimal totalValue = returns.stream().map(ReturnReportResponse.ReturnDetail::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        LinkedHashSet<fu.osms.common.enums.PlatformType> platforms = new LinkedHashSet<>();
        returns.stream().map(ReturnReportResponse.ReturnDetail::getPlatform)
                .filter(Objects::nonNull).forEach(platforms::add);
        OffsetDateTime lastReturnAt = returns.isEmpty() ? null : returns.get(0).getCreatedAt();
        return ReturnReportResponse.CustomerMetric.builder()
                .customerKey(accumulator.key)
                .customerId(accumulator.customerId)
                .customerName(accumulator.name)
                .customerPhone(accumulator.phone)
                .returnCount(returns.size())
                .totalValue(totalValue)
                .platforms(List.copyOf(platforms))
                .lastReturnAt(lastReturnAt)
                .returns(returns)
                .build();
    }

    private boolean isApproved(OrderReturnStatus status) {
        return status != null && status != OrderReturnStatus.PENDING_APPROVAL
                && status != OrderReturnStatus.REJECTED && status != OrderReturnStatus.FAILED;
    }

    private String customerKey(ReturnReportRowProjection row) {
        if (row.getCustomerId() != null) return "customer:" + row.getCustomerId();
        String identity = firstText(row.getBuyerPhone(), row.getCustomerPhone(), row.getBuyerName(), row.getExternalOrderId());
        return "guest:" + identity.toLowerCase(Locale.ROOT).trim();
    }

    private String customerName(ReturnReportRowProjection row) {
        return firstText(row.getCustomerName(), row.getBuyerName(), "Khách lẻ");
    }

    private String reason(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) return UNKNOWN_REASON;
        for (String key : List.of("returnReason", "reason", "reasonText", "buyerReason", "return_reason")) {
            Object value = metadata.get(key);
            if (value != null && !value.toString().isBlank()) return value.toString().trim();
        }
        return UNKNOWN_REASON;
    }

    private String firstText(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value.trim();
        return "-";
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal returnValue(List<ReturnReportResponse.ReturnItem> items) {
        return items.stream()
                .map(item -> money(item.getUnitPrice()).multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal percent(long value, long total) {
        return total == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(value).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    private record CustomerAccumulator(String key, UUID customerId, String name, String phone,
                                       List<ReturnReportResponse.ReturnDetail> returns) { }
}
