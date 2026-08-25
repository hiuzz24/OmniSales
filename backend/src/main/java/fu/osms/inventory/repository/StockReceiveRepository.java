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

    boolean existsByPurchaseOrderId(UUID purchaseOrderId);

    @Query("SELECT DISTINCT item.variant.id FROM InventoryReceiptItem item " +
            "WHERE item.receipt.status = 'CONFIRMED' " +
            "AND ((item.receipt.createdAt > :changedSince AND item.receipt.createdAt <= :changedUntil) " +
            "OR (item.receipt.updatedAt > :changedSince AND item.receipt.updatedAt <= :changedUntil))")
    List<UUID> findChangedConfirmedVariantIdsBetween(@Param("changedSince") OffsetDateTime changedSince,
                                                     @Param("changedUntil") OffsetDateTime changedUntil);

    @Query("SELECT DISTINCT item.variant.id FROM InventoryReceiptItem item " +
            "WHERE item.receipt.status = 'CONFIRMED' " +
            "AND (item.receipt.createdAt <= :changedUntil OR item.receipt.updatedAt <= :changedUntil)")
    List<UUID> findConfirmedVariantIdsUpTo(@Param("changedUntil") OffsetDateTime changedUntil);

    @Query("SELECT DISTINCT item.receipt.warehouse.id FROM InventoryReceiptItem item " +
            "WHERE item.receipt.status = 'CONFIRMED' " +
            "AND ((item.receipt.createdAt > :changedSince AND item.receipt.createdAt <= :changedUntil) " +
            "OR (item.receipt.updatedAt > :changedSince AND item.receipt.updatedAt <= :changedUntil))")
    List<UUID> findChangedConfirmedWarehouseIdsBetween(@Param("changedSince") OffsetDateTime changedSince,
                                                       @Param("changedUntil") OffsetDateTime changedUntil);

    @Query(value = "SELECT DISTINCT r.*, COALESCE(r.confirmed_at, r.updated_at) AS sort_time FROM inventory_receipts r " +
            "JOIN inventory_receipt_items iri ON iri.receipt_id = r.id " +
            "JOIN channel_product_variants cpv ON cpv.variant_id = iri.variant_id " +
            "JOIN channel_products cp ON cp.id = cpv.channel_product_id " +
            "JOIN channels ch ON ch.id = cp.channel_id " +
            "WHERE r.status = 'CONFIRMED' " +
            "AND ch.deleted_at IS NULL " +
            "AND cp.mapping_state = 'ACTIVE' " +
            "AND (cpv.last_synced_at IS NULL OR cpv.last_synced_at < COALESCE(r.confirmed_at, r.updated_at)) " +
            "ORDER BY sort_time ASC",
            nativeQuery = true)
    List<InventoryReceipt> findConfirmedReceiptsPendingMarketplaceSync();

    @Query("SELECT DISTINCT item.variant.id FROM InventoryReceiptItem item " +
            "WHERE item.receipt.id = :receiptId AND item.receipt.status = 'CONFIRMED'")
    List<UUID> findConfirmedVariantIdsByReceiptId(@Param("receiptId") UUID receiptId);

    @Query("SELECT COUNT(DISTINCT item.variant.id) FROM InventoryReceiptItem item " +
            "JOIN ChannelProductVariant cpv ON cpv.variant.id = item.variant.id " +
            "JOIN cpv.channelProduct cp " +
            "JOIN cp.channel ch " +
            "WHERE item.receipt.id = :receiptId " +
            "AND item.receipt.status = 'CONFIRMED' " +
            "AND ch.deletedAt IS NULL " +
            "AND cp.mappingState = 'ACTIVE' " +
            "AND (cpv.lastSyncedAt IS NULL OR cpv.lastSyncedAt < COALESCE(item.receipt.confirmedAt, item.receipt.updatedAt))")
    long countPendingMarketplaceSyncVariantsByReceiptId(@Param("receiptId") UUID receiptId);
}
