package fu.osms.reporting.repository.projection;

import java.math.BigDecimal;
import java.util.UUID;

public interface ReturnReportItemProjection {
    UUID getReturnId();
    String getSku();
    String getName();
    Integer getQuantity();
    BigDecimal getUnitPrice();
}
