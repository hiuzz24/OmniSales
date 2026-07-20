package fu.osms.inventory.repository;

import fu.osms.inventory.entity.InventoryReceipt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StockReceiveRepository extends JpaRepository<InventoryReceipt, UUID> {

    @Query("SELECT COUNT(r) FROM InventoryReceipt r WHERE YEAR(r.createdAt) = :year")
    long countByYear(@Param("year") int year);

    Optional<InventoryReceipt> findTopByReceiptCodeStartingWithOrderByReceiptCodeDesc(String prefix);

    Page<InventoryReceipt> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByStatus(String status);

    boolean existsByInvoiceNumber(String invoiceNumber);

    boolean existsByInvoiceNumberAndIdNot(String invoiceNumber, UUID id);

    @Query("SELECT DISTINCT item.variant.id FROM InventoryReceiptItem item " +
            "WHERE item.receipt.status = 'CONFIRMED' " +
            "AND item.receipt.updatedAt >= :changedSince " +
            "AND item.receipt.updatedAt <= :changedUntil")
    List<UUID> findChangedConfirmedVariantIdsBetween(@Param("changedSince") OffsetDateTime changedSince,
                                                     @Param("changedUntil") OffsetDateTime changedUntil);

    @Query("SELECT DISTINCT item.receipt.warehouse.id FROM InventoryReceiptItem item " +
            "WHERE item.receipt.status = 'CONFIRMED' " +
            "AND item.receipt.updatedAt >= :changedSince " +
            "AND item.receipt.updatedAt <= :changedUntil")
    List<UUID> findChangedConfirmedWarehouseIdsBetween(@Param("changedSince") OffsetDateTime changedSince,
                                                       @Param("changedUntil") OffsetDateTime changedUntil);

    @Query("SELECT DISTINCT item.variant.id FROM InventoryReceiptItem item " +
            "JOIN ChannelProductVariant cpv ON cpv.variant.id = item.variant.id " +
            "JOIN cpv.channelProduct cp " +
            "JOIN cp.channel ch " +
            "WHERE item.receipt.status = 'CONFIRMED' " +
            "AND ch.deletedAt IS NULL " +
            "AND cp.mappingState = 'ACTIVE' " +
            "AND (cpv.lastSyncedAt IS NULL OR cpv.lastSyncedAt < item.receipt.createdAt)")
    List<UUID> findConfirmedVariantIdsPendingMarketplaceSync();
}
