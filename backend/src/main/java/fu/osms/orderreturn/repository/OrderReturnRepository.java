package fu.osms.orderreturn.repository;

import fu.osms.orderreturn.entity.OrderReturn;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface OrderReturnRepository extends JpaRepository<OrderReturn, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from OrderReturn r where r.id = :id")
    Optional<OrderReturn> findForUpdateById(@Param("id") UUID id);

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
}
