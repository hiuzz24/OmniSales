package fu.osms.reporting.repository.projection;

import java.util.UUID;

public interface ProductCatalogProjection {
    UUID getProductId();
    UUID getVariantId();
    String getSku();
    String getProductName();
    String getVariantName();
}
