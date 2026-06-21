package fu.osms.customer.repository;

import fu.osms.customer.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID>, JpaSpecificationExecutor<Customer> {

    Optional<Customer> findByPhone(String phone);

    Optional<Customer> findByEmail(String email);

    @Query("SELECT c FROM Customer c WHERE " +
           "LOWER(c.code) LIKE :keyword OR " +
           "LOWER(c.fullName) LIKE :keyword OR " +
           "LOWER(c.phone) LIKE :keyword OR " +
           "LOWER(c.email) LIKE :keyword")
    Page<Customer> findAllBySearchKeyword(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT c FROM Customer c WHERE " +
           "(LOWER(c.code) LIKE :keyword OR " +
           "LOWER(c.fullName) LIKE :keyword OR " +
           "LOWER(c.phone) LIKE :keyword OR " +
           "LOWER(c.email) LIKE :keyword) " +
           "AND c.isActive = :isActive")
    Page<Customer> findAllBySearchKeywordAndStatus(
            @Param("keyword") String keyword,
            @Param("isActive") Boolean isActive,
            Pageable pageable);

    @Query("SELECT c FROM Customer c WHERE " +
           "(LOWER(c.code) LIKE :keyword OR " +
           "LOWER(c.fullName) LIKE :keyword OR " +
           "LOWER(c.phone) LIKE :keyword OR " +
           "LOWER(c.email) LIKE :keyword) " +
           "AND c.gender = :gender")
    Page<Customer> findAllBySearchKeywordAndGender(
            @Param("keyword") String keyword,
            @Param("gender") String gender,
            Pageable pageable);

    @Query("SELECT c FROM Customer c WHERE " +
           "(LOWER(c.code) LIKE :keyword OR " +
           "LOWER(c.fullName) LIKE :keyword OR " +
           "LOWER(c.phone) LIKE :keyword OR " +
           "LOWER(c.email) LIKE :keyword) " +
           "AND c.isActive = :isActive AND c.gender = :gender")
    Page<Customer> findAllBySearchKeywordAndStatusAndGender(
            @Param("keyword") String keyword,
            @Param("isActive") Boolean isActive,
            @Param("gender") String gender,
            Pageable pageable);

    Page<Customer> findAllByIsActive(Boolean isActive, Pageable pageable);

    Page<Customer> findAllByGender(String gender, Pageable pageable);

    Page<Customer> findAllByIsActiveAndGender(Boolean isActive, String gender, Pageable pageable);

    @Query("SELECT COUNT(c) FROM Customer c")
    long countAll();

    @Query("SELECT COUNT(c) FROM Customer c WHERE c.isActive = true")
    long countActive();

    @Query("SELECT COUNT(DISTINCT o.id) FROM Order o")
    long countAllOrders();

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o")
    BigDecimal sumTotalSpent();
}
