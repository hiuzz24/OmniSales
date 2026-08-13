package fu.osms.reporting.repository.projection;

import fu.osms.common.enums.PlatformType;
import fu.osms.orderreturn.enums.OrderReturnStatus;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public interface ReturnReportRowProjection {
    UUID getReturnId();
    UUID getOrderId();
    String getExternalOrderId();
    String getExternalReturnId();
    UUID getCustomerId();
    String getCustomerName();
    String getBuyerName();
    String getCustomerPhone();
    String getBuyerPhone();
    PlatformType getPlatform();
    String getChannelName();
    OrderReturnStatus getStatus();
    OffsetDateTime getCreatedAt();
    Map<String, Object> getMetadata();
}
