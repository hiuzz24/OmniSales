package fu.osms.reporting.repository.projection;

import java.util.UUID;

public interface ProductReturnProjection {
    UUID getVariantId();
    String getSku();
    Long getReturnedUnits();
}
