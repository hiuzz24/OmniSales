package fu.osms.inventory.service;

import fu.osms.order.entity.Order;

public interface PlatformOrderInventoryService {

    void syncReservations(Order order);
}
