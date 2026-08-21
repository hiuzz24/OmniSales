package fu.osms.inventory.service;

import fu.osms.inventory.dto.response.ReservationOutcome;

import java.util.Set;
import java.util.UUID;

public interface PlatformOrderInventoryService {

    ReservationOutcome tryReserve(UUID orderId);

    Set<UUID> releaseOrderReservations(UUID orderId);
}
