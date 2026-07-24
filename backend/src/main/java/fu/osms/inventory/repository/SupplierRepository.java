package fu.osms.inventory.repository;

import fu.osms.inventory.entity.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

    Page<Supplier> findByIsActiveTrueOrderByNameAsc(Pageable pageable);

    @Query("""
            SELECT s
            FROM Supplier s
            WHERE s.isActive = true
              AND (
                :keyword = ''
                OR LOWER(s.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(COALESCE(s.supplierCode, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(COALESCE(s.contactName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(COALESCE(s.phone, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
              )
            ORDER BY s.name ASC
            """)
    Page<Supplier> searchActive(@Param("keyword") String keyword, Pageable pageable);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

    Optional<Supplier> findByIdAndIsActiveTrue(UUID id);

    @Query("""
    SELECT s.supplierCode
    FROM Supplier s
    WHERE s.supplierCode IS NOT NULL
    ORDER BY s.supplierCode DESC
    LIMIT 1
""")
    String findLatestCode();

    Optional<Supplier> findTopByOrderBySupplierCodeDesc();

    Optional<Supplier> findTopBySupplierCodeStartingWithOrderBySupplierCodeDesc(String prefix);
}
