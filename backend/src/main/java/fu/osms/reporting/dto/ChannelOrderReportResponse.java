package fu.osms.reporting.dto;

import fu.osms.common.enums.PlatformType;
import fu.osms.order.enums.OrderStatus;
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
public class ChannelOrderReportResponse {
    private LocalDate fromDate;
    private LocalDate toDate;
    private long totalOrders;
    private BigDecimal totalRevenue;
    private BigDecimal averageOrderValue;
    private long deliveredOrders;
    private BigDecimal deliveredRate;
    private List<ChannelMetric> channels;
    private List<StatusMetric> statuses;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class ChannelMetric {
        private UUID channelId;
        private PlatformType platform;
        private String channelName;
        private long orderCount;
        private BigDecimal revenue;
        private BigDecimal averageOrderValue;
        private long deliveredCount;
        private long cancelledCount;
        private BigDecimal orderShare;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class StatusMetric {
        private OrderStatus status;
        private long orderCount;
    }
}
