package fu.osms.reporting.dto;

import fu.osms.common.enums.PlatformType;
import fu.osms.orderreturn.enums.OrderReturnStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class ReturnReportResponse {
    private LocalDate fromDate;
    private LocalDate toDate;
    private long totalRequests;
    private BigDecimal returnRate;
    private BigDecimal totalReturnValue;
    private long approvedRequests;
    private List<ReasonMetric> reasons;
    private List<CustomerMetric> customers;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class ReasonMetric {
        private String reason;
        private long count;
        private BigDecimal percentage;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class CustomerMetric {
        private String customerKey;
        private UUID customerId;
        private String customerName;
        private String customerPhone;
        private long returnCount;
        private BigDecimal totalValue;
        private List<PlatformType> platforms;
        private OffsetDateTime lastReturnAt;
        private List<ReturnDetail> returns;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class ReturnDetail {
        private UUID returnId;
        private UUID orderId;
        private String externalOrderId;
        private String externalReturnId;
        private PlatformType platform;
        private String channelName;
        private OrderReturnStatus status;
        private BigDecimal value;
        private String reason;
        private OffsetDateTime createdAt;
        private List<ReturnItem> items;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class ReturnItem {
        private String sku;
        private String name;
        private int quantity;
        private BigDecimal unitPrice;
    }
}
