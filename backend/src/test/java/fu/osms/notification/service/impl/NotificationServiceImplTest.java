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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationMapper notificationMapper;
    @Mock private EmailService emailService;

    private NotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new NotificationServiceImpl(notificationRepository, userRepository,
                notificationMapper, emailService);
    }

    private void setEmailEnabled(boolean enabled) {
        try {
            Field field = NotificationServiceImpl.class.getDeclaredField("emailEnabled");
            field.setAccessible(true);
            field.set(service, enabled);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("getByUser — maps Page<Notification> to PageResponse<NotificationResponse>")
    void getByUser_returnsPage() {
        UUID userId = UUID.randomUUID();
        Notification n = Notification.builder().id(UUID.randomUUID()).type("LOW_STOCK").title("t").build();
        NotificationResponse resp = NotificationResponse.builder().id(n.getId()).title("t").build();
        Page<Notification> page = new PageImpl<>(List.of(n), PageRequest.of(0, 20), 1);

        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(eq(userId), any())).thenReturn(page);
        when(notificationMapper.toResponse(n)).thenReturn(resp);

        PageResponse<NotificationResponse> result = service.getByUser(userId, 0, 20);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("t");
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("getUnread — calls findByUserIdAndReadAtIsNullOrderByCreatedAtDesc")
    void getUnread_returnsPage() {
        UUID userId = UUID.randomUUID();
        Page<Notification> page = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        when(notificationRepository.findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(eq(userId), any()))
                .thenReturn(page);

        PageResponse<NotificationResponse> result = service.getUnread(userId, 0, 10);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("countUnread — returns count from repo")
    void countUnread_returnsCount() {
        when(notificationRepository.countByUserIdAndReadAtIsNull(any(UUID.class))).thenReturn(7L);

        long count = service.countUnread(UUID.randomUUID());

        assertThat(count).isEqualTo(7L);
    }

    @Test
    @DisplayName("markAsRead — happy: sets readAt only if not already read")
    void markAsRead_happy() {
        UUID id = UUID.randomUUID();
        Notification n = Notification.builder().id(id).title("t").readAt(null).build();
        when(notificationRepository.findById(id)).thenReturn(Optional.of(n));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        service.markAsRead(id);

        assertThat(n.getReadAt()).isNotNull();
        verify(notificationRepository).save(n);
    }

    @Test
    @DisplayName("markAsRead — already read → no save (idempotent)")
    void markAsRead_idempotent() {
        UUID id = UUID.randomUUID();
        Notification n = Notification.builder().id(id).readAt(OffsetDateTime.now().minusMinutes(5)).build();
        when(notificationRepository.findById(id)).thenReturn(Optional.of(n));

        service.markAsRead(id);

        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    @DisplayName("markAsRead — not found → AppException(RESOURCE_NOT_FOUND)")
    void markAsRead_notFound() {
        UUID id = UUID.randomUUID();
        when(notificationRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markAsRead(id))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("markAllAsRead — returns repo row count")
    void markAllAsRead_returnsCount() {
        UUID userId = UUID.randomUUID();
        when(notificationRepository.markAllAsRead(userId)).thenReturn(5);

        int count = service.markAllAsRead(userId);

        assertThat(count).isEqualTo(5);
    }

    @Test
    @DisplayName("createNotification — user not found → AppException(USER_NOT_FOUND)")
    void createNotification_userNotFound() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createNotification(userId, "LOW_STOCK", "t", "b", "INVENTORY", UUID.randomUUID()))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("createNotification — happy without email: persists, no email sent")
    void createNotification_noEmail() {
        setEmailEnabled(false);
        UUID userId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        User user = User.builder().id(userId).email("u@osms.vn").build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        service.createNotification(userId, "LOW_STOCK", "Low stock", "SKU A low", "INVENTORY", entityId);

        ArgumentCaptor<Notification> cap = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(cap.capture());
        Notification saved = cap.getValue();
        assertThat(saved.getUser()).isEqualTo(user);
        assertThat(saved.getType()).isEqualTo("LOW_STOCK");
        assertThat(saved.getTitle()).isEqualTo("Low stock");
        assertThat(saved.getBody()).isEqualTo("SKU A low");
        assertThat(saved.getEntityType()).isEqualTo("INVENTORY");
        assertThat(saved.getEntityId()).isEqualTo(entityId);

        verify(emailService, never()).sendNotificationEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("createNotification — emailEnabled=true: persists + sends email with bracketed subject")
    void createNotification_sendsEmail() {
        setEmailEnabled(true);
        UUID userId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        User user = User.builder().id(userId).email("u@osms.vn").build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        service.createNotification(userId, "LOW_STOCK", "Low stock", "SKU A low", "INVENTORY", entityId);

        ArgumentCaptor<String> subjectCap = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(1)).sendNotificationEmail(eq("u@osms.vn"), subjectCap.capture(), eq("SKU A low"));
        assertThat(subjectCap.getValue()).isEqualTo("[OmniSales] Low stock");
    }
}
