package fu.osms.inventory.repository;
import fu.osms.inventory.dto.response.TransferSummaryDTO;
import fu.osms.inventory.entity.StockTransfer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StockTransferRepository extends JpaRepository<StockTransfer, UUID> {
    @Query("SELECT new fu.osms.inventory.dto.response.TransferSummaryDTO(" +
            "COUNT(st), " +
            "SUM(CASE WHEN st.status = 'COMPLETED' THEN 1 ELSE 0 END), " +
            "SUM(CASE WHEN st.status = 'IN_TRANSIT' THEN 1 ELSE 0 END), " +
            "SUM(CASE WHEN st.status = 'DRAFT' THEN 1 ELSE 0 END)) " +
            "FROM StockTransfer st")
    TransferSummaryDTO getTransferSummary();

    Page<StockTransfer> findAll(Pageable pageable);

    @Query("SELECT st FROM StockTransfer st WHERE " +
           "(:status IS NULL OR :status = '' OR st.status = :status) AND " +
           "(:warehouseId IS NULL OR st.fromWarehouse.id = :warehouseId OR st.toWarehouse.id = :warehouseId) AND " +
           "(:keyword IS NULL OR :keyword = '' OR " +
           " LOWER(st.transferCode) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           " LOWER(coalesce(st.note, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           " LOWER(st.fromWarehouse.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           " LOWER(st.toWarehouse.name) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<StockTransfer> searchTransfers(@Param("status") String status,
                                       @Param("warehouseId") UUID warehouseId,
                                       @Param("keyword") String keyword,
                                       Pageable pageable);
    @Query(value = "SELECT st.transfer_code FROM stock_transfers st WHERE st.transfer_code LIKE :prefix ORDER BY st.transfer_code DESC LIMIT 1", nativeQuery = true)
    Optional<String> findLatestTransferCodeByPrefix(@Param("prefix") String prefix);

}