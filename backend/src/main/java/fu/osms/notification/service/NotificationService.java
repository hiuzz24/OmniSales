package fu.osms.notification.service;

import fu.osms.common.dto.PageResponse;
import fu.osms.notification.dto.response.NotificationResponse;

import java.util.UUID;

public interface NotificationService {

    PageResponse<NotificationResponse> getByUser(UUID userId, int page, int size);

    PageResponse<NotificationResponse> getUnread(UUID userId, int page, int size);

    PageResponse<NotificationResponse> getAll(int page, int size);

    PageResponse<NotificationResponse> getAllUnread(int page, int size);

    long countUnread(UUID userId);

    long countAllUnread();

    void markAsRead(UUID id);

    int markAllAsRead(UUID userId);

    void createNotification(UUID userId, String type, String title, String body, String entityType, UUID entityId);
}
