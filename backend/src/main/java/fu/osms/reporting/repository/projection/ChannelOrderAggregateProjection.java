package fu.osms.reporting.repository.projection;

import fu.osms.common.enums.PlatformType;

import java.math.BigDecimal;
import java.util.UUID;

public interface ChannelOrderAggregateProjection {
    UUID getChannelId();

    PlatformType getPlatform();

    String getChannelName();

    Long getOrderCount();

    Long getValidOrderCount();

    BigDecimal getRevenue();

    Long getDeliveredCount();

    Long getCancelledCount();
}
