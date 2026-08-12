package fu.osms.reporting.repository.projection;

import java.math.BigDecimal;
import java.util.UUID;

public interface ProductSalesProjection {
    UUID getProductId();
    UUID getVariantId();
    String getSku();
    String getProductName();
    String getVariantName();
    Long getUnitsSold();
    BigDecimal getRevenue();
}
