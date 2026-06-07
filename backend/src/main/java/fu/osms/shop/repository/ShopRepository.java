package fu.osms.shop.repository;

import fu.osms.shop.entity.Shop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShopRepository extends JpaRepository<Shop, UUID> {

    Optional<Shop> findBySlug(String slug);

    boolean existsBySlug(String slug);

    java.util.List<Shop> findByIsActive(Boolean isActive);
}
