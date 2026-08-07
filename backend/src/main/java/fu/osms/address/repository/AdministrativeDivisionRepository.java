package fu.osms.address.repository;

import fu.osms.address.entity.AdministrativeDivision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdministrativeDivisionRepository extends JpaRepository<AdministrativeDivision, Long> {

    @Query("SELECT d FROM AdministrativeDivision d " +
            "WHERE d.countryCode = :countryCode AND d.level = :level " +
            "ORDER BY d.name ASC")
    List<AdministrativeDivision> findByCountryAndLevel(
            @Param("countryCode") String countryCode,
            @Param("level") Integer level);

    @Query("SELECT d FROM AdministrativeDivision d " +
            "WHERE d.countryCode = :countryCode AND d.parentCode = :parentCode " +
            "ORDER BY d.name ASC")
    List<AdministrativeDivision> findByCountryAndParent(
            @Param("countryCode") String countryCode,
            @Param("parentCode") String parentCode);

    @Query("SELECT d FROM AdministrativeDivision d " +
            "WHERE d.countryCode = :countryCode AND d.level = :level AND d.parentCode IS NULL " +
            "ORDER BY d.name ASC")
    List<AdministrativeDivision> findRootByCountryAndLevel(
            @Param("countryCode") String countryCode,
            @Param("level") Integer level);

    boolean existsByCountryCode(String countryCode);

    long countByCountryCodeAndLevel(String countryCode, Integer level);
}
