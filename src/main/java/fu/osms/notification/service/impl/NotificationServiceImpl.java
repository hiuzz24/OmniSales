package fu.osms.notification.service.impl;

import fu.osms.common.dto.PageResponse;
import fu.osms.notification.dto.response.NotificationResponse;
import fu.osms.notification.entity.Notification;
import fu.osms.notification.mapper.NotificationMapper;
import fu.osms.notification.repository.NotificationRepository;
import fu.osms.notification.service.NotificationService;
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
    private final NotificationMapper notificationMapper;

    @Override
    @Transactional(readOnly = true)
    public NotificationResponse getById(UUID id) {
        return notificationRepository.findById(id)
                .map(notificationMapper::toResponse)
                .orElseThrow(() -> new RuntimeException("Notification not found: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> getByUser(UUID shopId, UUID userId, int page, int size) {
        Page<Notification> pageResult = notificationRepository
                .findByShopIdAndUserIdOrderByCreatedAtDesc(shopId, userId, PageRequest.of(page, size));
        return toPageResponse(pageResult, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> getUnread(UUID shopId, UUID userId, int page, int size) {
        Page<Notification> pageResult = notificationRepository
                .findByShopIdAndUserIdAndReadAtIsNullOrderByCreatedAtDesc(shopId, userId, PageRequest.of(page, size));
        return toPageResponse(pageResult, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnread(UUID shopId, UUID userId) {
        return notificationRepository.countByShopIdAndUserIdAndReadAtIsNull(shopId, userId);
    }

    @Override
    @Transactional
    public NotificationResponse markAsRead(UUID id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Notification not found: " + id));
        notification.setReadAt(OffsetDateTime.now());
        return notificationMapper.toResponse(notificationRepository.save(notification));
    }

    @Override
    @Transactional
    public int markAllAsRead(UUID shopId, UUID userId) {
        return notificationRepository.markAllAsRead(shopId, userId);
    }

    private PageResponse<NotificationResponse> toPageResponse(Page<Notification> p, int page, int size) {
        return PageResponse.<NotificationResponse>builder()
                .content(p.getContent().stream().map(notificationMapper::toResponse).toList())
                .page(page).size(size)
                .totalElements(p.getTotalElements())
                .totalPages(p.getTotalPages())
                .first(p.isFirst()).last(p.isLast())
                .build();
    }
}
