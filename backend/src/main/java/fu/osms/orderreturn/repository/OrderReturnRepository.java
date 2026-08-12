package fu.osms.orderreturn.repository;

import fu.osms.orderreturn.entity.OrderReturn;
import fu.osms.orderreturn.enums.OrderReturnStatus;
import fu.osms.reporting.repository.projection.ReturnReportRowProjection;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.Collection;
import java.util.List;
import java.util.List;
import java.time.OffsetDateTime;
import java.util.UUID;

public interface OrderReturnRepository extends JpaRepository<OrderReturn, UUID> {

    @Query("""
            SELECT r.id AS returnId,
                   o.id AS orderId,
                   o.externalOrderId AS externalOrderId,
                   r.externalReturnId AS externalReturnId,
                   c.id AS customerId,
                   c.fullName AS customerName,
                   o.buyerName AS buyerName,
                   c.phone AS customerPhone,
                   o.buyerPhone AS buyerPhone,
                   r.platform AS platform,
                   ch.displayName AS channelName,
                   r.status AS status,
                   r.createdAt AS createdAt,
                   r.metadata AS metadata
            FROM OrderReturn r
                 JOIN r.order o
                 LEFT JOIN o.customer c
                 JOIN r.channel ch
            WHERE r.createdAt >= :from AND r.createdAt < :to
            ORDER BY r.createdAt DESC
            """)
    List<ReturnReportRowProjection> findReportRows(@Param("from") OffsetDateTime from,
                                                    @Param("to") OffsetDateTime to);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from OrderReturn r where r.id = :id")
    Optional<OrderReturn> findForUpdateById(@Param("id") UUID id);

    @Query("select r from OrderReturn r join fetch r.order join fetch r.channel left join fetch r.warehouse " +
            "where r.id = :id")
    Optional<OrderReturn> findWithDetailsById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from OrderReturn r "
            + "where r.channel.id = :channelId and r.externalReturnId = :externalReturnId")
    Optional<OrderReturn> findForUpdateByChannelIdAndExternalReturnId(
            @Param("channelId") UUID channelId,
            @Param("externalReturnId") String externalReturnId);

    @Query(value = "select r from OrderReturn r join fetch r.order join fetch r.channel left join fetch r.warehouse",
            countQuery = "select count(r) from OrderReturn r")
    Page<OrderReturn> findAllWithDetails(Pageable pageable);

    boolean existsByChannelIdAndExternalReturnId(UUID channelId, String externalReturnId);

    List<OrderReturn> findByRefundConfirmedAtIsNotNullAndInventoryPostedAtIsNullAndStatusInOrderByUpdatedAtAsc(
            Collection<OrderReturnStatus> statuses,
            Pageable pageable);
}
