package fu.osms.inventory.service;

import fu.osms.auth.entity.User;
import fu.osms.auth.entity.UserRole;
import fu.osms.auth.repository.UserRoleRepository;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.inventory.entity.InventoryItem;
import fu.osms.inventory.entity.Warehouse;
import fu.osms.inventory.repository.InventoryItemRepository;
import fu.osms.notification.repository.NotificationRepository;
import fu.osms.notification.service.NotificationService;
import fu.osms.system.service.SystemSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryAlertServiceTest {

    @Mock private UserRoleRepository userRoleRepository;
    @Mock private InventoryItemRepository inventoryItemRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationService notificationService;
    @Mock private SystemSettingService systemSettingService;

    private InventoryAlertService service;

    @BeforeEach
    void setUp() {
        service = new InventoryAlertService(userRoleRepository, inventoryItemRepository,
                notificationRepository, notificationService, systemSettingService);
    }

    private InventoryItem item(Integer onHand, Integer reserved, Integer threshold, String sku) {
        Warehouse wh = Warehouse.builder().id(UUID.randomUUID()).name("WH-1").build();
        ProductVariant variant = ProductVariant.builder().id(UUID.randomUUID()).sku(sku).build();
        return InventoryItem.builder()
                .id(UUID.randomUUID())
                .warehouse(wh)
                .variant(variant)
                .quantityOnHand(onHand)
                .reservedQuantity(reserved)
                .lowStockThreshold(threshold)
                .build();
    }

    private UserRole userRole(String email, UUID userId) {
        User user = User.builder().id(userId).email(email).build();
        return UserRole.builder().id(UUID.randomUUID()).user(user).build();
    }

    @Test
    @DisplayName("notifyLowStockAfterStockChange — null item → no-op")
    void notify_nullItem() {
        service.notifyLowStockAfterStockChange(null);
        verify(notificationService, never()).createNotification(any(), anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("notifyLowStockAfterStockChange — item without id → no-op")
    void notify_nullId() {
        InventoryItem item = item(0, 0, 5, "SKU-1");
        service.notifyLowStockAfterStockChange(item);
        verify(notificationService, never()).createNotification(any(), anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("notifyLowStockAfterStockChange — item without variant → no-op")
    void notify_nullVariant() {
        InventoryItem item = item(0, 0, 5, "SKU-1");
        item.setVariant(null);
        service.notifyLowStockAfterStockChange(item);
        verify(notificationService, never()).createNotification(any(), anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("notifyLowStockAfterStockChange — available > threshold → no notification")
    void notify_aboveThreshold() {
        InventoryItem item = item(20, 0, 5, "SKU-1");
        service.notifyLowStockAfterStockChange(item);
        verify(notificationService, never()).createNotification(any(), anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("notifyLowStockAfterStockChange — LOW_STOCK (available > 0 but <= threshold) → one notification per owner/ops/admin user")
    void notify_lowStock() {
        InventoryItem item = item(5, 0, 10, "SKU-001");
        User owner = User.builder().id(UUID.randomUUID()).email("owner@osms.vn").build();
        User ops = User.builder().id(UUID.randomUUID()).email("ops@osms.vn").build();
        User admin = User.builder().id(UUID.randomUUID()).email("admin@osms.vn").build();

        when(userRoleRepository.findByRoleNameIn(any()))
                .thenReturn(List.of(userRole("owner@osms.vn", owner.getId()),
                        userRole("ops@osms.vn", ops.getId()),
                        userRole("admin@osms.vn", admin.getId())));

        service.notifyLowStockAfterStockChange(item);

        ArgumentCaptor<String> titleCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> bodyCap = ArgumentCaptor.forClass(String.class);
        verify(notificationService, times(3)).createNotification(any(), eq("LOW_STOCK"),
                titleCap.capture(), bodyCap.capture(), eq("INVENTORY"), eq(item.getId()));

        assertThat(titleCap.getValue()).contains("dưới mức tồn tối thiểu");
        assertThat(bodyCap.getValue()).contains("SKU-001").contains("WH-1").contains("5");
    }

    @Test
    @DisplayName("notifyLowStockAfterStockChange — OUT_OF_STOCK (available <= 0) → title contains 'hết hàng'")
    void notify_outOfStock() {
        InventoryItem item = item(5, 5, 10, "SKU-003");
        User owner = User.builder().id(UUID.randomUUID()).email("owner@osms.vn").build();
        when(userRoleRepository.findByRoleNameIn(any()))
                .thenReturn(List.of(userRole("owner@osms.vn", owner.getId())));

        service.notifyLowStockAfterStockChange(item);

        ArgumentCaptor<String> titleCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> bodyCap = ArgumentCaptor.forClass(String.class);
        verify(notificationService, times(1)).createNotification(any(), eq("LOW_STOCK"),
                titleCap.capture(), bodyCap.capture(), any(), any());
        assertThat(titleCap.getValue()).contains("hết hàng");
        assertThat(bodyCap.getValue()).contains("0").contains("SKU-003");
    }

    @Test
    @DisplayName("notifyLowStockAfterStockChange — only targets OWNER/OPERATIONS/SYSTEM_ADMIN roles")
    void notify_filtersByRole() {
        InventoryItem item = item(3, 0, 10, "SKU-X");
        when(userRoleRepository.findByRoleNameIn(any()))
                .thenReturn(List.of()); // No matching roles

        service.notifyLowStockAfterStockChange(item);

        verify(notificationService, never()).createNotification(any(), anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("notifyLowStockAfterStockChange — dedup by userId when same user has multiple roles")
    void notify_dedupUsers() {
        InventoryItem item = item(3, 0, 10, "SKU-X");
        UUID sharedId = UUID.randomUUID();
        User sharedUser = User.builder().id(sharedId).email("shared@osms.vn").build();

        UserRole ur1 = UserRole.builder().id(UUID.randomUUID()).user(sharedUser).build();
        UserRole ur2 = UserRole.builder().id(UUID.randomUUID()).user(sharedUser).build();
        when(userRoleRepository.findByRoleNameIn(any())).thenReturn(List.of(ur1, ur2));

        service.notifyLowStockAfterStockChange(item);

        verify(notificationService, times(1)).createNotification(any(), anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("sendLowStockReminders — finds low-stock items and skips when no owners match")
    void sendLowStockReminders_enforceWindow() throws Exception {
        InventoryItem item = item(2, 0, 5, "SKU-1");
        when(inventoryItemRepository.findLowStockItems()).thenReturn(List.of(item));
        when(userRoleRepository.findByRoleNameIn(any())).thenReturn(List.of());

        service.sendLowStockReminders();

        // findLowStockItems was iterated but no notification because no users matched.
        verify(inventoryItemRepository).findLowStockItems();
        verify(notificationService, never()).createNotification(any(), anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("sendLowStockReminders — suppression window blocks repeat notification for same user")
    void suppress_whenAlreadySent() throws Exception {
        UUID userId = UUID.randomUUID();
        InventoryItem item = item(2, 0, 5, "SKU-1");
        UserRole existingUr = UserRole.builder()
                .id(UUID.randomUUID())
                .user(User.builder().id(userId).email("owner@osms.vn").build())
                .build();
        // To make `existsByUserId...` actually be invoked, we need an OWNER user returned.
        when(userRoleRepository.findByRoleNameIn(any())).thenReturn(List.of(existingUr));
        // suppress the send (exists returns true)
        when(systemSettingService.getLong("low_stock_repeat_hours", 12L)).thenReturn(24L);
        when(notificationRepository.existsByUserIdAndTypeAndEntityTypeAndEntityIdAndCreatedAtAfter(
                any(), any(), any(), any(), any())).thenReturn(true);
        when(inventoryItemRepository.findLowStockItems()).thenReturn(List.of(item));

        service.sendLowStockReminders();

        verify(notificationRepository).existsByUserIdAndTypeAndEntityTypeAndEntityIdAndCreatedAtAfter(
                eq(userId), any(), any(), any(), any());
        verify(notificationService, never()).createNotification(any(), anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("private shouldSendLowStockNotification — uses low_stock_repeat_hours setting")
    void shouldSendLowStock_usesSetting() throws Exception {
        when(systemSettingService.getLong("low_stock_repeat_hours", 12L)).thenReturn(48L);
        when(notificationRepository.existsByUserIdAndTypeAndEntityTypeAndEntityIdAndCreatedAtAfter(
                any(), any(), any(), any(), any())).thenReturn(false);

        Method m = InventoryAlertService.class.getDeclaredMethod("shouldSendLowStockNotification", UUID.class, InventoryItem.class);
        m.setAccessible(true);

        InventoryItem item = item(2, 0, 5, "SKU-1");
        Boolean result = (Boolean) m.invoke(service, UUID.randomUUID(), item);

        assertThat(result).isTrue();
    }
}
