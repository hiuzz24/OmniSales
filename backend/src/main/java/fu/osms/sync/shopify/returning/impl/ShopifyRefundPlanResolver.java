package fu.osms.sync.shopify.returning.impl;

import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.orderreturn.model.ReturnActionContext;
import fu.osms.sync.shopify.returning.ShopifyRefundPlan;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class ShopifyRefundPlanResolver {

    List<Map<String, Object>> requestedLines(ReturnActionContext context) {
        return context.items().stream()
                .filter(item -> item.externalReturnItemId() != null && !item.externalReturnItemId().isBlank()
                        && item.receivedQuantity() != null && item.receivedQuantity() > 0)
                .map(item -> Map.<String, Object>of(
                        "id", item.externalReturnItemId(),
                        "quantity", item.receivedQuantity()))
                .toList();
    }

    ShopifyRefundPlan resolve(Map<String, Object> responseData) {
        Map<String, Object> returnData = object(responseData.get("return"));
        Map<String, Object> outcome = object(returnData.get("suggestedFinancialOutcome"));
        Map<String, Object> transfer = object(outcome.get("financialTransfer"));
        if (!"RefundReturnOutcome".equals(String.valueOf(transfer.get("__typename")))) {
            throw new AppException(ErrorCode.INVALID_REQUEST,
                    "Shopify không thể tạo kế hoạch hoàn tiền cho yêu cầu trả hàng này");
        }
        List<ShopifyRefundPlan.OrderTransaction> transactions = maps(transfer.get("suggestedTransactions"))
                .stream().map(this::transaction).filter(Objects::nonNull).toList();
        if (transactions.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_REQUEST,
                    "Shopify không trả về giao dịch thanh toán hợp lệ để hoàn tiền");
        }
        return new ShopifyRefundPlan(transactions);
    }

    private ShopifyRefundPlan.OrderTransaction transaction(Map<String, Object> source) {
        String parentId = String.valueOf(object(source.get("parentTransaction")).get("id"));
        Map<String, Object> presentment = object(object(source.get("amountSet")).get("presentmentMoney"));
        String amountText = String.valueOf(presentment.get("amount"));
        String currencyCode = String.valueOf(presentment.get("currencyCode"));
        if (parentId.isBlank() || "null".equals(parentId) || amountText.isBlank() || "null".equals(amountText)
                || currencyCode.isBlank() || "null".equals(currencyCode)) return null;
        try {
            BigDecimal amount = new BigDecimal(amountText);
            return amount.signum() > 0
                    ? new ShopifyRefundPlan.OrderTransaction(parentId, amount, currencyCode) : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Map<String, Object> object(Object value) {
        if (!(value instanceof Map<?, ?> source)) return new LinkedHashMap<>();
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof List<?> values)) return List.of();
        return values.stream().filter(Map.class::isInstance).map(this::object).toList();
    }
}
