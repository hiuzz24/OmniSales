package fu.osms.notification.service.impl;

import fu.osms.auth.entity.User;
import fu.osms.auth.repository.UserRepository;
import fu.osms.auth.service.EmailService;
import fu.osms.common.dto.PageResponse;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.notification.dto.response.NotificationResponse;
import fu.osms.notification.entity.Notification;
import fu.osms.notification.mapper.NotificationMapper;
import fu.osms.notification.repository.NotificationRepository;
import fu.osms.notification.service.NotificationService;
import fu.osms.system.service.SystemSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final NotificationMapper notificationMapper;
    private final EmailService emailService;
    private final SystemSettingService systemSettingService;

    @Value("${app.notifications.email-enabled:false}")
    private boolean emailEnabled;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> getByUser(UUID userId, int page, int size) {
        Page<Notification> notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(
                userId, PageRequest.of(page, size));
        return toPageResponse(notifications);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> getUnread(UUID userId, int page, int size) {
        Page<Notification> notifications = notificationRepository.findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(
                userId, PageRequest.of(page, size));
        return toPageResponse(notifications);
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnread(UUID userId) {
        return notificationRepository.countByUserIdAndReadAtIsNull(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> getAll(int page, int size) {
        Page<Notification> notifications = notificationRepository.findAllByOrderByCreatedAtDesc(
                PageRequest.of(page, size));
        return toPageResponse(notifications);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> getAllUnread(int page, int size) {
        Page<Notification> notifications = notificationRepository.findByReadAtIsNullOrderByCreatedAtDesc(
                PageRequest.of(page, size));
        return toPageResponse(notifications);
    }

    @Override
    @Transactional(readOnly = true)
    public long countAllUnread() {
        return notificationRepository.countByReadAtIsNull();
    }

    @Override
    @Transactional
    public void markAsRead(UUID id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
        if (notification.getReadAt() == null) {
            notification.setReadAt(OffsetDateTime.now());
            notificationRepository.save(notification);
        }
    }

    @Override
    @Transactional
    public void markAsRead(UUID id, UUID userId) {
        Notification notification = notificationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
        if (notification.getReadAt() == null) {
            notification.setReadAt(OffsetDateTime.now());
            notificationRepository.save(notification);
        }
    }

    @Override
    @Transactional
    public int markAllAsRead(UUID userId) {
        return notificationRepository.markAllAsRead(userId);
    }

    @Override
    @Transactional
    public void createNotification(UUID userId, String type, String title, String body, String entityType, UUID entityId) {
        if (!isNotificationTypeEnabled(type)) {
            return;
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        Notification notification = Notification.builder()
                .user(user)
                .type(type)
                .title(title)
                .body(body)
                .entityType(entityType)
                .entityId(entityId)
                .build();
        notificationRepository.save(notification);

        if (systemSettingService.getBoolean("notification_email_enabled", emailEnabled)) {
            emailService.sendNotificationEmail(user.getEmail(), "[OmniSales] " + title, body);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean createNotificationIfAbsent(UUID userId, String type, String title, String body,
                                              String entityType, UUID entityId) {
        if (!isNotificationTypeEnabled(type)) {
            return false;
        }
        if (entityId != null && notificationRepository
                .existsByUserIdAndTypeAndEntityTypeAndEntityId(userId, type, entityType, entityId)) {
            return false;
        }
        createNotification(userId, type, title, body, entityType, entityId);
        return true;
    }

    private boolean isNotificationTypeEnabled(String type) {
        if (type == null) {
            return true;
        }
        if (type.startsWith("ORDER_RETURN_")) {
            return systemSettingService.getBoolean("notification_return_enabled", true);
        }
        if (type.startsWith("ORDER_")) {
            return systemSettingService.getBoolean("notification_order_enabled", true);
        }
        return switch (type) {
            case "LOW_STOCK" -> systemSettingService.getBoolean("notification_low_stock_enabled", true);
            case "SYNC_FAILED" -> systemSettingService.getBoolean("notification_sync_failure_enabled", true);
            case "CHANNEL_DISCONNECTED" -> systemSettingService.getBoolean(
                    "notification_channel_disconnected_enabled", true);
            default -> true;
        };
    }

    private PageResponse<NotificationResponse> toPageResponse(Page<Notification> page) {
        return PageResponse.<NotificationResponse>builder()
                .content(page.getContent().stream().map(notificationMapper::toResponse).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }
}
