package fu.osms.inventory.repository;

import fu.osms.inventory.entity.InventoryTransaction;
import fu.osms.inventory.enums.InvTxnType;
import jakarta.persistence.criteria.Join;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.UUID;

public class InventoryTransactionSpecification {

    public static Specification<InventoryTransaction> filterLogs(
            UUID warehouseId,
            String productSearch,
            InvTxnType type,
            OffsetDateTime startDate,
            OffsetDateTime endDate,
            UUID performedById) {

        return (root, query, cb) -> {
            var predicate = cb.conjunction();

            // 1. Lọc theo kho hàng
            if (warehouseId != null) {
                predicate = cb.and(predicate, cb.equal(root.get("warehouse").get("id"), warehouseId));
            }

            // 2. Lọc theo Loại giao dịch (Enum)
            if (type != null) {
                predicate = cb.and(predicate, cb.equal(root.get("type"), type));
            }

            // 3. Lọc theo khoảng thời gian (Timestamp)
            if (startDate != null) {
                predicate = cb.and(predicate, cb.greaterThanOrEqualTo(root.get("performedAt"), startDate));
            }
            if (endDate != null) {
                predicate = cb.and(predicate, cb.lessThanOrEqualTo(root.get("performedAt"), endDate));
            }

            // 4. Lọc theo người thực hiện
            if (performedById != null) {
                predicate = cb.and(predicate, cb.equal(root.get("performedBy").get("id"), performedById));
            }

            // 5. Tìm kiếm theo SKU hoặc Tên sản phẩm (Bắt buộc JOIN sang bảng Variant và Product)
            if (StringUtils.hasText(productSearch)) {
                String searchPattern = "%" + productSearch.trim().toLowerCase() + "%";
                Join<Object, Object> variantJoin = root.join("variant");
                Join<Object, Object> productJoin = variantJoin.join("product"); // Giả định ProductVariant có quan hệ 'product'

                var skuLike = cb.like(cb.lower(variantJoin.get("sku")), searchPattern);
                var productNameLike = cb.like(cb.lower(productJoin.get("name")), searchPattern);

                predicate = cb.and(predicate, cb.or(skuLike, productNameLike));
            }

            return predicate;
        };
    }
}