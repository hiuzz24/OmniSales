package fu.osms.notification.repository;

import fu.osms.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<Notification> findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    long countByUserIdAndReadAtIsNull(UUID userId);

    boolean existsByUserIdAndEntityTypeAndEntityIdAndReadAtIsNull(UUID userId, String entityType, UUID entityId);

    boolean existsByUserIdAndTypeAndEntityTypeAndEntityIdAndCreatedAtAfter(
            UUID userId, String type, String entityType, UUID entityId, OffsetDateTime createdAt);

    @Modifying
    @Query("UPDATE Notification n SET n.readAt = CURRENT_TIMESTAMP " +
           "WHERE n.user.id = :userId AND n.readAt IS NULL")
    int markAllAsRead(@Param("userId") UUID userId);
}
