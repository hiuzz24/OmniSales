package fu.osms.inventory.repository;

import fu.osms.inventory.entity.InventoryReceipt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface StockReceiveRepository extends JpaRepository<InventoryReceipt, UUID> {

    @Query("SELECT COUNT(r) FROM InventoryReceipt r WHERE YEAR(r.createdAt) = :year")
    long countByYear(@Param("year") int year);

    Page<InventoryReceipt> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByStatus(String status);

    boolean existsByInvoiceNumber(String invoiceNumber);

    boolean existsByInvoiceNumberAndIdNot(String invoiceNumber, UUID id);
}
