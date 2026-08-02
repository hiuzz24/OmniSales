package fu.osms.sync.shopify.returning;

import java.math.BigDecimal;
import java.util.List;

public record ShopifyRefundPlan(List<OrderTransaction> orderTransactions) {

    public ShopifyRefundPlan {
        orderTransactions = List.copyOf(orderTransactions);
    }

    public record OrderTransaction(String parentId, BigDecimal amount, String currencyCode) {
    }
}
