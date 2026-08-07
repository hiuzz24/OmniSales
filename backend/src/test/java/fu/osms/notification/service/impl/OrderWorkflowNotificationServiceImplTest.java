package fu.osms.notification.service.impl;

import fu.osms.auth.entity.User;
import fu.osms.auth.entity.UserRole;
import fu.osms.auth.repository.UserRoleRepository;
import fu.osms.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderWorkflowNotificationServiceImpl Tests")
class OrderWorkflowNotificationServiceImplTest {

    @Mock private UserRoleRepository userRoleRepository;
    @Mock private NotificationService notificationService;

    private OrderWorkflowNotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrderWorkflowNotificationServiceImpl(userRoleRepository, notificationService);
    }

    private UserRole userRole(UUID userId) {
        User user = User.builder().id(userId).email(userId + "@example.com").build();
        return UserRole.builder().user(user).build();
    }

    @Test
    @DisplayName("notifyRoles: fans out one notification per unique user")
    void notifyRoles_fansOutByUserId() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        when(userRoleRepository.findByRoleNameIn(any()))
                .thenReturn(List.of(userRole(userA), userRole(userB), userRole(userA))); // duplicate userA

        service.notifyRoles(List.of("MANAGER", "ADMIN"), "ORDER_CREATED",
                "New order", "Please check", "ORDER", UUID.randomUUID());

        verify(notificationService, times(2))
                .createNotification(any(), eq("ORDER_CREATED"), eq("New order"),
                        eq("Please check"), eq("ORDER"), any());
    }

    @Test
    @DisplayName("notifyRoles: no notification is created when no users match the roles")
    void notifyRoles_noMatchingUsers() {
        when(userRoleRepository.findByRoleNameIn(any())).thenReturn(List.of());

        service.notifyRoles(List.of("ADMIN"), "ORDER_CREATED",
                "New order", "Please check", "ORDER", UUID.randomUUID());

        verify(notificationService, never()).createNotification(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("notifyRoles: passes every provided field to the notification service")
    void notifyRoles_passesAllFields() {
        UUID userId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        when(userRoleRepository.findByRoleNameIn(any())).thenReturn(List.of(userRole(userId)));

        service.notifyRoles(List.of("MANAGER"), "ORDER_SHIPPED",
                "Order shipped", "Carrier assigned", "ORDER", entityId);

        verify(notificationService).createNotification(userId,
                "ORDER_SHIPPED", "Order shipped", "Carrier assigned", "ORDER", entityId);
    }

    @Test
    @DisplayName("notifyRoles: continues to notify remaining users when one notification throws")
    void notifyRoles_continuesOnError() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        when(userRoleRepository.findByRoleNameIn(any()))
                .thenReturn(List.of(userRole(userA), userRole(userB)));
        // First call throws, second call is a no-op. We verify the loop continued.
        org.mockito.Mockito.doThrow(new RuntimeException("notification failed"))
                .when(notificationService).createNotification(eq(userA), any(), any(), any(), any(), any());

        assertThatCode(() -> service.notifyRoles(List.of("ADMIN"),
                "ORDER_CREATED", "t", "b", "ORDER", UUID.randomUUID()))
                .doesNotThrowAnyException();

        verify(notificationService).createNotification(eq(userA),
                any(), any(), any(), any(), any());
        verify(notificationService).createNotification(eq(userB),
                any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("notifyRoles: works with a null roles collection (empty repo lookup)")
    void notifyRoles_nullRoles() {
        when(userRoleRepository.findByRoleNameIn(isNull())).thenReturn(List.of());

        service.notifyRoles(null, "ORDER_CREATED",
                "t", "b", "ORDER", UUID.randomUUID());

        verify(notificationService, never()).createNotification(any(), any(), any(), any(), any(), any());
    }
}