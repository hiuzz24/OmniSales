package fu.osms.notification.service;

import java.util.Collection;
import java.util.UUID;

public interface OrderWorkflowNotificationService {

    void notifyRoles(
            Collection<String> roles,
            String type,
            String title,
            String body,
            String entityType,
            UUID entityId
    );

    void notifyRolesOnce(
            Collection<String> roles,
            String type,
            String title,
            String body,
            String entityType,
            UUID entityId
    );
}
