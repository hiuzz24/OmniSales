package fu.osms.inventory.service;

import fu.osms.auth.entity.User;
import fu.osms.inventory.entity.InventoryIssue;
import fu.osms.inventory.entity.InventoryIssueItem;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public interface OrderGiftReservationService {

    Set<UUID> reserveGiftReservations(
            InventoryIssue issue,
            Collection<InventoryIssueItem> giftItems,
            User actor
    );

    Set<UUID> releaseGiftReservations(
            InventoryIssue issue,
            User actor
    );

    void commitGiftReservations(
            InventoryIssue issue,
            User actor
    );
}
