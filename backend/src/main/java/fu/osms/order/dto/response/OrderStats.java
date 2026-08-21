package fu.osms.order.dto.response;

import fu.osms.audit.dto.response.AuditLogResponse;
import fu.osms.common.dto.PageResponse;
import fu.osms.order.service.OrderService;
import fu.osms.order.enums.OrderStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderStats(
    long totalOrders,
    long pendingCount,
    long waitingStockCount,
    long confirmedCount,
    long processingCount,
    long shippedCount,
    long deliveredCount,
    long cancelledCount,
    BigDecimal totalRevenue
) {}
