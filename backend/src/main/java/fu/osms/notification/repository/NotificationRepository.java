package fu.osms.notification.repository;

import fu.osms.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByShopIdAndUserIdOrderByCreatedAtDesc(UUID shopId, UUID userId, Pageable pageable);

    Page<Notification> findByShopIdAndUserIdAndReadAtIsNullOrderByCreatedAtDesc(UUID shopId, UUID userId, Pageable pageable);

    long countByShopIdAndUserIdAndReadAtIsNull(UUID shopId, UUID userId);

    @Modifying
    @Query("UPDATE Notification n SET n.readAt = CURRENT_TIMESTAMP " +
           "WHERE n.shop.id = :shopId AND n.user.id = :userId AND n.readAt IS NULL")
    int markAllAsRead(@Param("shopId") UUID shopId, @Param("userId") UUID userId);
}
