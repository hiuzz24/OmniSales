package fu.osms.notification.service;

import fu.osms.common.dto.PageResponse;
import fu.osms.notification.dto.response.NotificationResponse;

import java.util.UUID;

public interface NotificationService {

    NotificationResponse getById(UUID id);

    PageResponse<NotificationResponse> getByUser(UUID shopId, UUID userId, int page, int size);

    PageResponse<NotificationResponse> getUnread(UUID shopId, UUID userId, int page, int size);

    long countUnread(UUID shopId, UUID userId);

    NotificationResponse markAsRead(UUID id);

    int markAllAsRead(UUID shopId, UUID userId);
}
