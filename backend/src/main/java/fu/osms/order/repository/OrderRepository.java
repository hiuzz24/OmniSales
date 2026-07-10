package fu.osms.order.repository;

import fu.osms.order.entity.Order;
import fu.osms.order.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    Page<Order> findByChannelId(UUID channelId, Pageable pageable);

    Optional<Order> findByExternalOrderId(String externalOrderId);

    Optional<Order> findByChannel_IdAndExternalOrderId(UUID channelId, String externalOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.channel.id = :channelId AND o.externalOrderId = :externalOrderId")
    Optional<Order> findForUpdateByChannelIdAndExternalOrderId(@Param("channelId") UUID channelId,
                                                               @Param("externalOrderId") String externalOrderId);

    @Modifying
    @Query(value = """
            INSERT INTO orders (
                id,
                channel_id,
                platform,
                channel_name,
                external_order_id,
                status,
                payment_status,
                shipping_address,
                subtotal,
                discount_amount,
                shipping_fee,
                currency,
                version,
                created_at,
                updated_at
            )
            VALUES (
                :id,
                :channelId,
                CAST(:platform AS platform_type),
                :channelName,
                :externalOrderId,
                CAST('PENDING' AS order_status),
                'UNPAID',
                '{}'::jsonb,
                0,
                0,
                0,
                'VND',
                0,
                NOW(),
                NOW()
            )
            ON CONFLICT (channel_id, external_order_id)
            WHERE channel_id IS NOT NULL
            DO NOTHING
            """, nativeQuery = true)
    void insertWebhookOrderIfAbsent(@Param("id") UUID id,
                                    @Param("channelId") UUID channelId,
                                    @Param("platform") String platform,
                                    @Param("channelName") String channelName,
                                    @Param("externalOrderId") String externalOrderId);

    Page<Order> findByCustomerId(UUID customerId, Pageable pageable);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.customer.id = :customerId")
    Long countByCustomerId(@Param("customerId") UUID customerId);

    @Query("SELECT COALESCE(SUM(o.subtotal - o.discountAmount + o.shippingFee), 0) FROM Order o WHERE o.customer.id = :customerId")
    BigDecimal sumTotalSpentByCustomerId(@Param("customerId") UUID customerId);

    @Query("SELECT o FROM Order o WHERE o.createdAt BETWEEN :from AND :to")
    Page<Order> findByDateRange(@Param("from") OffsetDateTime from,
                                @Param("to") OffsetDateTime to,
                                Pageable pageable);

    @Query("SELECT COUNT(o) FROM Order o")
    long countAll();

    long countByStatus(OrderStatus status);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.status = 'DELIVERED'")
    BigDecimal sumRevenueDelivered();
}
