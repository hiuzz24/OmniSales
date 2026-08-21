package fu.osms.order.repository;

import fu.osms.order.entity.Order;
import fu.osms.order.enums.OrderStatus;
import fu.osms.reporting.repository.projection.ChannelOrderAggregateProjection;
import fu.osms.reporting.repository.projection.OrderStatusAggregateProjection;
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
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {

    @Query("""
            SELECT o.channel.id AS channelId,
                   o.platform AS platform,
                   o.channelName AS channelName,
                   COUNT(o) AS orderCount,
                   SUM(CASE WHEN o.status <> 'CANCELLED' THEN 1 ELSE 0 END) AS validOrderCount,
                   COALESCE(SUM(CASE WHEN o.status <> 'CANCELLED' THEN o.totalAmount ELSE 0 END), 0) AS revenue,
                   SUM(CASE WHEN o.status = 'DELIVERED' THEN 1 ELSE 0 END) AS deliveredCount,
                   SUM(CASE WHEN o.status = 'CANCELLED' THEN 1 ELSE 0 END) AS cancelledCount
            FROM Order o
            WHERE o.createdAt >= :from AND o.createdAt < :to
            GROUP BY o.channel.id, o.platform, o.channelName
            ORDER BY COUNT(o) DESC
            """)
    List<ChannelOrderAggregateProjection> aggregateOrdersByChannel(@Param("from") OffsetDateTime from,
                                                                    @Param("to") OffsetDateTime to);

    @Query("""
            SELECT o.status AS status, COUNT(o) AS orderCount
            FROM Order o
            WHERE o.createdAt >= :from AND o.createdAt < :to
            GROUP BY o.status
            ORDER BY COUNT(o) DESC
            """)
    List<OrderStatusAggregateProjection> aggregateOrderStatuses(@Param("from") OffsetDateTime from,
                                                                 @Param("to") OffsetDateTime to);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.status = :status AND o.createdAt >= :from AND o.createdAt < :to")
    long countByStatusInDateRange(@Param("status") OrderStatus status,
                                  @Param("from") OffsetDateTime from,
                                  @Param("to") OffsetDateTime to);

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    Page<Order> findByChannelId(UUID channelId, Pageable pageable);

    Optional<Order> findByExternalOrderId(String externalOrderId);

    Optional<Order> findByChannel_IdAndExternalOrderId(UUID channelId, String externalOrderId);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.channel WHERE o.id = :id")
    Optional<Order> findByIdWithChannel(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findForUpdateById(@Param("id") UUID id);

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
    int insertPlatformOrderIfAbsent(@Param("id") UUID id,
                                    @Param("channelId") UUID channelId,
                                    @Param("platform") String platform,
                                    @Param("channelName") String channelName,
                                    @Param("externalOrderId") String externalOrderId);

    Page<Order> findByCustomerId(UUID customerId, Pageable pageable);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.customer.id = :customerId AND o.status <> 'CANCELLED'")
    Long countByCustomerId(@Param("customerId") UUID customerId);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.customer.id = :customerId AND o.status <> 'CANCELLED'")
    BigDecimal sumTotalSpentByCustomerId(@Param("customerId") UUID customerId);

    @Query("SELECT DISTINCT o.customer.id FROM Order o WHERE o.customer IS NOT NULL AND o.status <> 'CANCELLED'")
    List<UUID> findCustomerIdsWithNonNullCustomer();

    @Query("SELECT o FROM Order o WHERE o.customer IS NULL ORDER BY o.createdAt ASC")
    List<Order> findAllByCustomerIsNullOrderByCreatedAtAsc();

    @Query("SELECT COUNT(o) FROM Order o WHERE o.customer IS NULL")
    long countByCustomerIsNull();

    @Query("SELECT new fu.osms.order.repository.projection.CustomerOrderAggregate(" +
            "o.customer.id, COUNT(o), COALESCE(SUM(o.totalAmount), 0)) " +
            "FROM Order o " +
            "WHERE o.customer.id IN :customerIds " +
            "AND o.status <> 'CANCELLED' " +
            "GROUP BY o.customer.id")
    List<fu.osms.order.repository.projection.CustomerOrderAggregate> aggregateByCustomerIds(
            @Param("customerIds") Collection<UUID> customerIds);

    @Query("SELECT o FROM Order o WHERE o.createdAt BETWEEN :from AND :to")
    Page<Order> findByDateRange(@Param("from") OffsetDateTime from,
                                @Param("to") OffsetDateTime to,
                                Pageable pageable);

    @Query("SELECT COUNT(o) FROM Order o")
    long countAll();

    long countByStatus(OrderStatus status);

    @Query(value = """
            SELECT o.id
            FROM orders o
            WHERE o.status = 'WAITING_STOCK'
              AND EXISTS (
                    SELECT 1 FROM order_items oi
                    WHERE oi.order_id = o.id AND oi.variant_id IN (:variantIds)
              )
            ORDER BY o.waiting_stock_at ASC NULLS LAST, o.created_at ASC, o.id ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<UUID> findWaitingStockCandidateIds(@Param("variantIds") Collection<UUID> variantIds,
                                            @Param("limit") int limit);

    @Query(value = """
            SELECT o.id
            FROM orders o
            WHERE o.status = 'PENDING'
              AND o.platform IN ('SHOPIFY', 'LAZADA')
              AND EXISTS (
                    SELECT 1 FROM order_items oi
                    WHERE oi.order_id = o.id AND oi.variant_id IN (:variantIds)
              )
            ORDER BY o.created_at ASC, o.id ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<UUID> findPendingStockCandidateIds(@Param("variantIds") Collection<UUID> variantIds,
                                            @Param("limit") int limit);

    @Query(value = """
            SELECT id FROM orders
            WHERE status = 'WAITING_STOCK'
              AND waiting_stock_expires_at <= :now
              AND waiting_stock_expiry_notified_at IS NULL
            ORDER BY waiting_stock_expires_at ASC, id ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<UUID> findExpiredWaitingStockIds(@Param("now") OffsetDateTime now, @Param("limit") int limit);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.status = 'DELIVERED'")
    BigDecimal sumRevenueDelivered();

    @Query(value = """
            SELECT o FROM Order o
            WHERE o.status = :status
              AND (
                    :keyword IS NULL
                    OR :keyword = ''
                    OR LOWER(o.externalOrderId) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(COALESCE(o.buyerName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR EXISTS (
                        SELECT oi.id FROM OrderItem oi
                        WHERE oi.order = o
                          AND (
                            LOWER(COALESCE(oi.sku, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                            OR LOWER(COALESCE(oi.name, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                          )
                    )
              )
              AND NOT EXISTS (
                    SELECT issue.id FROM InventoryIssue issue
                    WHERE issue.referenceId = o.id
                      AND issue.issueType = 'ORDER'
                      AND issue.status IN ('DRAFT', 'CONFIRMED')
              )
              AND COALESCE(function('jsonb_extract_path_text', o.platformMetadata,
                    'tiktok', 'buyerCancellation', 'active'), 'false') <> 'true'
            ORDER BY
              CASE WHEN :orderId IS NOT NULL AND o.id = :orderId THEN 0 ELSE 1 END,
              o.createdAt DESC
            """,
            countQuery = """
            SELECT COUNT(o) FROM Order o
            WHERE o.status = :status
              AND (
                    :keyword IS NULL
                    OR :keyword = ''
                    OR LOWER(o.externalOrderId) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(COALESCE(o.buyerName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR EXISTS (
                        SELECT oi.id FROM OrderItem oi
                        WHERE oi.order = o
                          AND (
                            LOWER(COALESCE(oi.sku, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                            OR LOWER(COALESCE(oi.name, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                          )
                    )
              )
              AND NOT EXISTS (
                    SELECT issue.id FROM InventoryIssue issue
                    WHERE issue.referenceId = o.id
                      AND issue.issueType = 'ORDER'
                      AND issue.status IN ('DRAFT', 'CONFIRMED')
              )
              AND COALESCE(function('jsonb_extract_path_text', o.platformMetadata,
                    'tiktok', 'buyerCancellation', 'active'), 'false') <> 'true'
            """)
    Page<Order> findStockDeliveryCandidates(@Param("status") OrderStatus status,
                                            @Param("orderId") UUID orderId,
                                            @Param("keyword") String keyword,
                                            Pageable pageable);
}
