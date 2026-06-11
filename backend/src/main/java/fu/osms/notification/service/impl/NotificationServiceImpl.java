package fu.osms.notification.service.impl;

import fu.osms.common.dto.PageResponse;
import fu.osms.notification.dto.response.NotificationResponse;
import fu.osms.notification.entity.Notification;
import fu.osms.notification.mapper.NotificationMapper;
import fu.osms.notification.repository.NotificationRepository;
import fu.osms.notification.service.NotificationService;
import fu.osms.auth.entity.User;
import fu.osms.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final NotificationMapper notificationMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> getByUser(UUID userId, int page, int size) {
        throw new UnsupportedOperationException("Chưa code");
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> getUnread(UUID userId, int page, int size) {
        throw new UnsupportedOperationException("Chưa code");
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnread(UUID userId) {
        throw new UnsupportedOperationException("Chưa code");
    }

    @Override
    @Transactional
    public void markAsRead(UUID id) {
        throw new UnsupportedOperationException("Chưa code");
    }

    @Override
    @Transactional
    public int markAllAsRead(UUID userId) {
        throw new UnsupportedOperationException("Chưa code");
    }

    @Override
    @Transactional
    public void createNotification(UUID userId, String type, String title, String body, String entityType, UUID entityId) {
        throw new UnsupportedOperationException("Chưa code");
    }

    private PageResponse<NotificationResponse> toPageResponse(Page<Notification> p, int page, int size) {
        throw new UnsupportedOperationException("Chưa code");
    }
}
