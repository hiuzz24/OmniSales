package fu.osms.catalog.repository;

import fu.osms.catalog.entity.ProductLog;
import fu.osms.catalog.enums.ProductLogAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProductLogRepository extends JpaRepository<ProductLog, UUID> {

    Page<ProductLog> findByProductIdOrderByPerformedAtDesc(UUID productId, Pageable pageable);

    Page<ProductLog> findByVariantIdOrderByPerformedAtDesc(UUID variantId, Pageable pageable);

    Page<ProductLog> findByShopIdAndActionOrderByPerformedAtDesc(UUID shopId,
                                                                  ProductLogAction action,
                                                                  Pageable pageable);

    Page<ProductLog> findByShopIdOrderByPerformedAtDesc(UUID shopId, Pageable pageable);

    Page<ProductLog> findBySkuOrderByPerformedAtDesc(String sku, Pageable pageable);
}
