package fu.osms.order.repository;

import fu.osms.order.entity.Order;
import fu.osms.order.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    Page<Order> findByShopId(UUID shopId, Pageable pageable);

    Page<Order> findByShopIdAndStatus(UUID shopId, OrderStatus status, Pageable pageable);

    Page<Order> findByShopIdAndChannelId(UUID shopId, UUID channelId, Pageable pageable);

    Optional<Order> findByShopIdAndExternalOrderId(UUID shopId, String externalOrderId);

    @Query("SELECT o FROM Order o WHERE o.shop.id = :shopId " +
           "AND o.createdAt BETWEEN :from AND :to")
    Page<Order> findByShopIdAndDateRange(@Param("shopId") UUID shopId,
                                          @Param("from") OffsetDateTime from,
                                          @Param("to") OffsetDateTime to,
                                          Pageable pageable);
}
