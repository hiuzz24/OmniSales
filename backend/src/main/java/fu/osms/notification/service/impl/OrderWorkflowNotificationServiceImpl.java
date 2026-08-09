package fu.osms.notification.service.impl;

import fu.osms.auth.entity.UserRole;
import fu.osms.auth.repository.UserRoleRepository;
import fu.osms.notification.service.NotificationService;
import fu.osms.notification.service.OrderWorkflowNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderWorkflowNotificationServiceImpl implements OrderWorkflowNotificationService {

    private final UserRoleRepository userRoleRepository;
    private final NotificationService notificationService;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyRoles(
            Collection<String> roles,
            String type,
            String title,
            String body,
            String entityType,
            UUID entityId
    ) {
        LinkedHashSet<UUID> userIds = new LinkedHashSet<>();
        for (UserRole userRole : userRoleRepository.findByRoleNameIn(roles)) {
            userIds.add(userRole.getUser().getId());
        }

        for (UUID userId : userIds) {
            try {
                notificationService.createNotification(
                        userId, type, title, body, entityType, entityId);
            } catch (Exception exception) {
                log.warn(
                        "Could not create order workflow notification userId={}, type={}, entityId={}: {}",
                        userId, type, entityId, exception.getMessage());
            }
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyRolesOnce(
            Collection<String> roles,
            String type,
            String title,
            String body,
            String entityType,
            UUID entityId
    ) {
        LinkedHashSet<UUID> userIds = new LinkedHashSet<>();
        for (UserRole userRole : userRoleRepository.findByRoleNameIn(roles)) {
            userIds.add(userRole.getUser().getId());
        }

        for (UUID userId : userIds) {
            try {
                notificationService.createNotificationIfAbsent(
                        userId, type, title, body, entityType, entityId);
            } catch (Exception exception) {
                log.warn("Could not create notification userId={}, type={}, entityId={}: {}",
                        userId, type, entityId, exception.getMessage());
            }
        }
    }
}
