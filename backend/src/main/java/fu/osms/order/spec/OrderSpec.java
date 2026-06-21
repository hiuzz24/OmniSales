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
            OffsetDateTime to
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            if (channelId != null) {
                predicates.add(cb.equal(root.get("channel").get("id"), channelId));
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

            query.orderBy(cb.desc(root.get("createdAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
