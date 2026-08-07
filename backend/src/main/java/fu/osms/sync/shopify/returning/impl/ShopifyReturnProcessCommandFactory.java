package fu.osms.sync.shopify.returning.impl;

import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.orderreturn.model.ReturnActionContext;
import fu.osms.sync.shopify.returning.ShopifyRefundPlan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class ShopifyReturnProcessCommandFactory {

    Map<String, Object> build(String returnGid,
                              ReturnActionContext context,
                              Map<String, Object> detail,
                              String locationId,
                              ShopifyRefundPlan refundPlan) {
        validateFullReceipt(context);
        List<Map<String, Object>> lines = processLines(context, detail, locationId);
        if (lines.isEmpty()) {
            throw new AppException(ErrorCode.ORDER_RETURN_ITEM_IDENTITY_MISSING);
        }
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("returnId", returnGid);
        input.put("returnLineItems", lines);
        input.put("financialTransfer", Map.of(
                "issueRefund", Map.of("orderTransactions", refundTransactions(refundPlan))));
        input.put("notifyCustomer", true);
        return input;
    }

    private List<Map<String, Object>> processLines(ReturnActionContext context,
                                                   Map<String, Object> detail,
                                                   String locationId) {
        Map<String, Map<String, Object>> returnLinesById = connectionNodes(object(detail.get("returnLineItems")))
                .stream().filter(line -> text(line.get("id")) != null)
                .collect(Collectors.toMap(line -> text(line.get("id")), line -> line,
                        (first, ignored) -> first, LinkedHashMap::new));
        Map<String, String> reverseLineByFulfillmentLine = reverseLineByFulfillmentLine(detail);
        List<Map<String, Object>> result = new ArrayList<>();
        for (ReturnActionContext.Item item : context.items()) {
            if (item.receivedQuantity() == null || item.receivedQuantity() <= 0) continue;
            Map<String, Object> returnLine = returnLinesById.get(item.externalReturnItemId());
            String fulfillmentLineId = text(object(returnLine == null
                    ? null : returnLine.get("fulfillmentLineItem")).get("id"));
            String reverseLineId = reverseLineByFulfillmentLine.get(fulfillmentLineId);
            if (returnLine == null || reverseLineId == null) {
                throw new AppException(ErrorCode.ORDER_RETURN_ITEM_IDENTITY_MISSING,
                        "Shopify reverse fulfillment line is missing for return item "
                                + item.externalReturnItemId());
            }
            List<Map<String, Object>> dispositions = dispositions(item, reverseLineId, locationId);
            int dispositionQuantity = dispositions.stream().mapToInt(value -> integer(value.get("quantity"))).sum();
            if (dispositionQuantity != item.receivedQuantity()) {
                throw new AppException(ErrorCode.ORDER_RETURN_QUANTITY_INVALID,
                        "Shopify disposition quantity does not match received quantity for return item "
                                + item.externalReturnItemId());
            }
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("id", item.externalReturnItemId());
            line.put("quantity", item.receivedQuantity());
            line.put("dispositions", dispositions);
            result.add(line);
        }
        return result;
    }

    private List<Map<String, Object>> dispositions(ReturnActionContext.Item item,
                                                   String reverseLineId,
                                                   String locationId) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (positive(item.restockableQuantity()) > 0) {
            Map<String, Object> disposition = new LinkedHashMap<>();
            disposition.put("reverseFulfillmentOrderLineItemId", reverseLineId);
            disposition.put("quantity", positive(item.restockableQuantity()));
            disposition.put("locationId", locationId);
            disposition.put("dispositionType", "RESTOCKED");
            result.add(disposition);
        }
        if (positive(item.damagedQuantity()) > 0) {
            result.add(Map.of(
                    "reverseFulfillmentOrderLineItemId", reverseLineId,
                    "quantity", positive(item.damagedQuantity()),
                    "dispositionType", "NOT_RESTOCKED"));
        }
        return result;
    }

    private Map<String, String> reverseLineByFulfillmentLine(Map<String, Object> detail) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map<String, Object> order : connectionNodes(object(detail.get("reverseFulfillmentOrders")))) {
            for (Map<String, Object> line : connectionNodes(object(order.get("lineItems")))) {
                String reverseLineId = text(line.get("id"));
                String fulfillmentLineId = text(object(line.get("fulfillmentLineItem")).get("id"));
                if (reverseLineId != null && fulfillmentLineId != null) {
                    result.putIfAbsent(fulfillmentLineId, reverseLineId);
                }
            }
        }
        return result;
    }

    void validateFullReceipt(ReturnActionContext context) {
        boolean partial = context.items().stream().anyMatch(item -> item.receivedQuantity() == null
                || item.receivedQuantity() != item.approvedQuantity()
                || positive(item.missingQuantity()) != 0);
        if (partial) throw new AppException(ErrorCode.ORDER_RETURN_PARTIAL_REQUIRES_MANUAL);
    }

    private List<Map<String, Object>> refundTransactions(ShopifyRefundPlan plan) {
        return plan.orderTransactions().stream().map(transaction -> Map.<String, Object>of(
                "parentId", transaction.parentId(),
                "transactionAmount", Map.of(
                        "amount", transaction.amount(),
                        "currencyCode", transaction.currencyCode()))).toList();
    }

    private Map<String, Object> object(Object value) {
        if (!(value instanceof Map<?, ?> source)) return new LinkedHashMap<>();
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private List<Map<String, Object>> connectionNodes(Map<String, Object> connection) {
        Object nodes = connection.get("nodes");
        if (nodes instanceof List<?>) return maps(nodes);
        return maps(connection.get("edges")).stream().map(edge -> object(edge.get("node"))).toList();
    }

    private List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof List<?> values)) return List.of();
        return values.stream().filter(Map.class::isInstance).map(this::object).toList();
    }

    private String text(Object value) {
        return value == null || value.toString().isBlank() ? null : value.toString();
    }

    private int integer(Object value) {
        try { return value == null ? 0 : Integer.parseInt(value.toString()); }
        catch (NumberFormatException ignored) { return 0; }
    }

    private int positive(Integer value) {
        return value == null ? 0 : Math.max(value, 0);
    }
}
