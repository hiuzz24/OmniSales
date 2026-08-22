package fu.osms.order.spec;

import fu.osms.order.entity.Order;
import fu.osms.order.enums.OrderStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class OrderSpec {

    private OrderSpec() {}

    public static Specification<Order> withFilters(
            OrderStatus status,
            UUID channelId,
            String keyword,
            OffsetDateTime from,
            OffsetDateTime to,
            UUID customerId,
            Boolean waitingStockExpired,
            OffsetDateTime requestNow
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            if (channelId != null) {
                predicates.add(cb.equal(root.get("channel").get("id"), channelId));
            }

            if (customerId != null) {
                predicates.add(cb.equal(root.get("customer").get("id"), customerId));
            }

            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.toLowerCase() + "%";
                Predicate byExternalId = cb.like(
                        cb.lower(root.get("externalOrderId")), pattern);
                Predicate byBuyerName = cb.like(
                        cb.lower(root.get("buyerName")), pattern);
                predicates.add(cb.or(byExternalId, byBuyerName));
            }

            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }

            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }

            if (Boolean.TRUE.equals(waitingStockExpired)) {
                predicates.add(cb.equal(root.get("status"), OrderStatus.WAITING_STOCK));
                predicates.add(cb.isNotNull(root.get("waitingStockExpiresAt")));
                predicates.add(cb.lessThanOrEqualTo(root.get("waitingStockExpiresAt"), requestNow));
            }

            if (status == OrderStatus.PENDING || status == OrderStatus.WAITING_STOCK) {
                query.orderBy(
                        cb.asc(cb.selectCase()
                                .when(cb.isNotNull(root.get("waitingStockAt")), 0)
                                .otherwise(1)),
                        cb.asc(root.get("waitingStockAt")),
                        cb.desc(root.get("createdAt")));
            } else {
                query.orderBy(cb.desc(root.get("createdAt")));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
