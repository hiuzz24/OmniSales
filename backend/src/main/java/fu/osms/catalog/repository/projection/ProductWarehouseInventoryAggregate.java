package fu.osms.catalog.repository.projection;

import java.util.UUID;

public interface ProductWarehouseInventoryAggregate {
    UUID getWarehouseId();

    String getWarehouseName();

    Long getQuantityOnHand();

    Long getReservedQuantity();

    Long getAvailableQuantity();

    Long getLowStockCount();
}
