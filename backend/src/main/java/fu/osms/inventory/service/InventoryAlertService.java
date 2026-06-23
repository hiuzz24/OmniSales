package fu.osms.inventory.service;

import fu.osms.auth.repository.UserRoleRepository;
import fu.osms.inventory.entity.InventoryItem;
import fu.osms.inventory.repository.InventoryItemRepository;
import fu.osms.notification.repository.NotificationRepository;
import fu.osms.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryAlertService {

    private static final String ENTITY_TYPE = "INVENTORY";
    private static final String LOW_STOCK_TYPE = "LOW_STOCK";

    private final UserRoleRepository userRoleRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;

    @Value("${app.notifications.low-stock-repeat-hours:12}")
    private long lowStockRepeatHours;

    public void notifyLowStockAfterStockChange(InventoryItem item) {
        notifyLowStockIfNeeded(item, false);
    }

    private void notifyLowStockIfNeeded(InventoryItem item, boolean enforceRepeatWindow) {
        if (item == null || item.getId() == null || item.getVariant() == null) {
            return;
        }

        int available = safeInt(item.getQuantityOnHand()) - safeInt(item.getReservedQuantity());
        int reorderLevel = safeInt(item.getLowStockThreshold());
        if (available > reorderLevel) {
            return;
        }

        String sku = item.getVariant().getSku();
        String warehouseName = item.getWarehouse() != null ? item.getWarehouse().getName() : "kho";
        String title = available <= 0 ? "SKU đã hết hàng" : "SKU dưới mức tồn tối thiểu";
        String body = sku + " tại " + warehouseName + " còn có thể bán " + available
                + " / mức tối thiểu " + reorderLevel + ".";

        userRoleRepository.findByRoleNameIn(List.of("OWNER", "OPERATIONS")).stream()
                .map(userRole -> userRole.getUser().getId())
                .distinct()
                .filter(userId -> !enforceRepeatWindow || shouldSendLowStockNotification(userId, item))
                .forEach(userId -> notificationService.createNotification(
                        userId,
                        LOW_STOCK_TYPE,
                        title,
                        body,
                        ENTITY_TYPE,
                        item.getId()));
    }

    @Scheduled(
            fixedRateString = "${app.notifications.low-stock-reminder-ms:43200000}",
            initialDelayString = "${app.notifications.low-stock-reminder-initial-delay-ms:43200000}"
    )
    public void sendLowStockReminders() {
        inventoryItemRepository.findLowStockItems().forEach(item -> notifyLowStockIfNeeded(item, true));
    }

    private boolean shouldSendLowStockNotification(java.util.UUID userId, InventoryItem item) {
        OffsetDateTime since = OffsetDateTime.now().minusHours(lowStockRepeatHours);
        return !notificationRepository.existsByUserIdAndTypeAndEntityTypeAndEntityIdAndCreatedAtAfter(
                userId, LOW_STOCK_TYPE, ENTITY_TYPE, item.getId(), since);
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }
}
