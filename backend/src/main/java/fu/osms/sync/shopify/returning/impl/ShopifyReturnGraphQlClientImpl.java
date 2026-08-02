package fu.osms.sync.shopify.returning.impl;

import fu.osms.channel.entity.Channel;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.channel.token.service.ChannelTokenService;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.orderreturn.model.ReturnActionContext;
import fu.osms.sync.shopify.ShopifyApiClient;
import fu.osms.sync.shopify.inventory.ShopifyInventoryGateway;
import fu.osms.sync.shopify.returning.ShopifyReturnGraphQlClient;
import fu.osms.sync.shopify.returning.ShopifyRefundPlan;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

@Component
@RequiredArgsConstructor
public class ShopifyReturnGraphQlClientImpl implements ShopifyReturnGraphQlClient {

    private static final String RETURN_FIELDS = """
            id
            name
            status
            createdAt
            closedAt
            requestApprovedAt
            order { id legacyResourceId }
            returnLineItems(first: 100) {
              nodes {
                __typename
                id
                quantity
                processableQuantity
                processedQuantity
                refundableQuantity
                refundedQuantity
                ... on ReturnLineItem {
                  fulfillmentLineItem {
                    id
                    lineItem { id sku name }
                  }
                }
              }
            }
            reverseFulfillmentOrders(first: 20) {
              nodes {
                id
                lineItems(first: 100) {
                  nodes {
                    id
                    totalQuantity
                    fulfillmentLineItem { id }
                    dispositions {
                      quantity
                      type
                      location { id }
                    }
                  }
                }
              }
            }
            refunds(first: 20) {
              nodes {
                id
                transactions(first: 20) {
                  nodes { id status kind }
                }
                refundLineItems(first: 100) {
                  nodes { quantity lineItem { id } }
                }
              }
            }
            """;

    private final ShopifyApiClient apiClient;
    private final ChannelTokenService tokenService;
    private final ChannelRepository channelRepository;
    private final ShopifyInventoryGateway inventoryGateway;

    @Override
    public Map<String, Object> getReturn(UUID channelId, String returnGid) {
        String query = "query ReturnDetail($id: ID!) { return(id: $id) { " + RETURN_FIELDS + " } }";
        Map<String, Object> response = execute(channelId, query, Map.of("id", returnGid));
        return object(data(response).get("return"));
    }

    @Override
    public Map<String, Object> approve(UUID channelId, String returnGid) {
        String mutation = """
                mutation ApproveReturn($input: ReturnApproveRequestInput!) {
                  returnApproveRequest(input: $input) {
                    return { %s }
                    userErrors { field message code }
                  }
                }
                """.formatted(RETURN_FIELDS);
        Map<String, Object> response = execute(channelId, mutation, Map.of("input", Map.of("id", returnGid)));
        return mutationReturn(response, "returnApproveRequest");
    }

    @Override
    public Map<String, Object> decline(UUID channelId, String returnGid, String reasonCode, String comment) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("id", returnGid);
        input.put("declineReason", reasonCode);
        if (comment != null && !comment.isBlank()) input.put("declineNote", comment.trim());
        input.put("notifyCustomer", true);
        String mutation = """
                mutation DeclineReturn($input: ReturnDeclineRequestInput!) {
                  returnDeclineRequest(input: $input) {
                    return { %s }
                    userErrors { field message code }
                  }
                }
                """.formatted(RETURN_FIELDS);
        Map<String, Object> response = execute(channelId, mutation, Map.of("input", input));
        return mutationReturn(response, "returnDeclineRequest");
    }

    @Override
    public Map<String, Object> process(UUID channelId, String returnGid, ReturnActionContext context) {
        requireFullReceipt(context);
        Map<String, Object> detail = getReturn(channelId, returnGid);
        boolean hasRestockableItems = context.items().stream()
                .anyMatch(item -> positive(item.restockableQuantity()) > 0);
        String locationId = hasRestockableItems
                ? inventoryGateway.resolveManagedLocationId(channelId)
                : null;
        List<Map<String, Object>> lines = processLines(context, detail, locationId);
        if (lines.isEmpty()) {
            throw new AppException(ErrorCode.ORDER_RETURN_ITEM_IDENTITY_MISSING);
        }
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("returnId", returnGid);
        input.put("returnLineItems", lines);
        input.put("financialTransfer", Map.of(
                "issueRefund", Map.of("orderTransactions", refundTransactions(
                        suggestedRefundPlan(channelId, returnGid, context)))));
        input.put("notifyCustomer", true);
        String mutation = """
                mutation ProcessReturn($input: ReturnProcessInput!) {
                  returnProcess(input: $input) {
                    return { %s }
                    userErrors { field message code }
                  }
                }
                """.formatted(RETURN_FIELDS);
        Map<String, Object> response = execute(channelId, mutation, Map.of("input", input));
        return mutationReturn(response, "returnProcess");
    }

    private List<Map<String, Object>> processLines(ReturnActionContext context,
                                                   Map<String, Object> detail,
                                                   String locationId) {
        Map<String, Map<String, Object>> returnLinesById = connectionNodes(
                object(detail.get("returnLineItems"))).stream()
                .filter(line -> text(line.get("id")) != null)
                .collect(java.util.stream.Collectors.toMap(
                        line -> text(line.get("id")),
                        line -> line,
                        (first, ignored) -> first,
                        LinkedHashMap::new));
        Map<String, String> reverseLineByFulfillmentLine = reverseLineByFulfillmentLine(detail);

        List<Map<String, Object>> result = new ArrayList<>();
        for (ReturnActionContext.Item item : context.items()) {
            if (item.receivedQuantity() == null || item.receivedQuantity() <= 0) {
                continue;
            }
            Map<String, Object> returnLine = returnLinesById.get(item.externalReturnItemId());
            String fulfillmentLineId = text(object(returnLine == null
                    ? null
                    : returnLine.get("fulfillmentLineItem")).get("id"));
            String reverseLineId = reverseLineByFulfillmentLine.get(fulfillmentLineId);
            if (returnLine == null || reverseLineId == null) {
                throw new AppException(ErrorCode.ORDER_RETURN_ITEM_IDENTITY_MISSING,
                        "Shopify reverse fulfillment line is missing for return item "
                                + item.externalReturnItemId());
            }

            List<Map<String, Object>> dispositions = new ArrayList<>();
            if (positive(item.restockableQuantity()) > 0) {
                Map<String, Object> disposition = new LinkedHashMap<>();
                disposition.put("reverseFulfillmentOrderLineItemId", reverseLineId);
                disposition.put("quantity", positive(item.restockableQuantity()));
                disposition.put("locationId", locationId);
                disposition.put("dispositionType", "RESTOCKED");
                dispositions.add(disposition);
            }
            if (positive(item.damagedQuantity()) > 0) {
                dispositions.add(Map.of(
                        "reverseFulfillmentOrderLineItemId", reverseLineId,
                        "quantity", positive(item.damagedQuantity()),
                        "dispositionType", "NOT_RESTOCKED"));
            }
            int dispositionQuantity = dispositions.stream()
                    .mapToInt(value -> integer(value.get("quantity")))
                    .sum();
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

    private Map<String, String> reverseLineByFulfillmentLine(Map<String, Object> detail) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map<String, Object> order : connectionNodes(
                object(detail.get("reverseFulfillmentOrders")))) {
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

    private void requireFullReceipt(ReturnActionContext context) {
        boolean partial = context.items().stream().anyMatch(item ->
                item.receivedQuantity() == null
                        || item.receivedQuantity() != item.approvedQuantity()
                        || positive(item.missingQuantity()) != 0);
        if (partial) {
            throw new AppException(ErrorCode.ORDER_RETURN_PARTIAL_REQUIRES_MANUAL);
        }
    }

    private ShopifyRefundPlan suggestedRefundPlan(UUID channelId,
                                                   String returnGid,
                                                   ReturnActionContext context) {
        List<Map<String, Object>> requestedLines = context.items().stream()
                .filter(item -> item.externalReturnItemId() != null
                        && !item.externalReturnItemId().isBlank()
                        && item.receivedQuantity() != null
                        && item.receivedQuantity() > 0)
                .map(item -> Map.<String, Object>of(
                        "id", item.externalReturnItemId(),
                        "quantity", item.receivedQuantity()))
                .toList();
        String query = """
                query SuggestedReturnOutcome(
                  $id: ID!,
                  $returnLineItems: [SuggestedOutcomeReturnLineItemInput!]!
                ) {
                  return(id: $id) {
                    suggestedFinancialOutcome(
                      returnLineItems: $returnLineItems,
                      exchangeLineItems: []
                    ) {
                      financialTransfer {
                        __typename
                        ... on RefundReturnOutcome {
                          suggestedTransactions {
                            parentTransaction { id }
                            amountSet {
                              presentmentMoney { amount currencyCode }
                            }
                          }
                        }
                      }
                    }
                  }
                }
                """;
        Map<String, Object> response = execute(channelId, query, Map.of(
                "id", returnGid,
                "returnLineItems", requestedLines));
        Map<String, Object> returnData = object(data(response).get("return"));
        Map<String, Object> outcome = object(returnData.get("suggestedFinancialOutcome"));
        Map<String, Object> transfer = object(outcome.get("financialTransfer"));
        if (!"RefundReturnOutcome".equals(String.valueOf(transfer.get("__typename")))) {
            throw new AppException(ErrorCode.INVALID_REQUEST,
                    "Shopify không thể tạo kế hoạch hoàn tiền cho yêu cầu trả hàng này");
        }
        List<ShopifyRefundPlan.OrderTransaction> transactions = maps(transfer.get("suggestedTransactions"))
                .stream()
                .map(this::refundTransaction)
                .filter(Objects::nonNull)
                .toList();
        if (transactions.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_REQUEST,
                    "Shopify không trả về giao dịch thanh toán hợp lệ để hoàn tiền");
        }
        return new ShopifyRefundPlan(transactions);
    }

    private ShopifyRefundPlan.OrderTransaction refundTransaction(Map<String, Object> source) {
        String parentId = String.valueOf(object(source.get("parentTransaction")).get("id"));
        Map<String, Object> presentment = object(object(source.get("amountSet")).get("presentmentMoney"));
        String amountText = String.valueOf(presentment.get("amount"));
        String currencyCode = String.valueOf(presentment.get("currencyCode"));
        if (parentId.isBlank() || "null".equals(parentId)
                || amountText.isBlank() || "null".equals(amountText)
                || currencyCode.isBlank() || "null".equals(currencyCode)) {
            return null;
        }
        try {
            BigDecimal amount = new BigDecimal(amountText);
            return amount.signum() > 0
                    ? new ShopifyRefundPlan.OrderTransaction(parentId, amount, currencyCode)
                    : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private List<Map<String, Object>> refundTransactions(ShopifyRefundPlan plan) {
        return plan.orderTransactions().stream()
                .map(transaction -> Map.<String, Object>of(
                        "parentId", transaction.parentId(),
                        "transactionAmount", Map.of(
                                "amount", transaction.amount(),
                                "currencyCode", transaction.currencyCode())))
                .toList();
    }

    private Map<String, Object> execute(UUID channelId, String query, Map<String, Object> variables) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new AppException(ErrorCode.CHANNEL_NOT_FOUND));
        String shop = metadataText(channel, "shopDomain");
        if (shop == null) throw new AppException(ErrorCode.CHANNEL_NOT_CONNECTED, "Shopify shop domain is missing");
        return tokenService.execute(channelId,
                token -> apiClient.executeGraphQl(shop, token.accessToken(), query, variables));
    }

    private Map<String, Object> mutationReturn(Map<String, Object> response, String mutationName) {
        Map<String, Object> payload = object(data(response).get(mutationName));
        List<Map<String, Object>> errors = maps(payload.get("userErrors"));
        if (!errors.isEmpty()) {
            String message = errors.stream().map(error -> String.valueOf(error.get("message")))
                    .filter(value -> !value.isBlank()).reduce((a, b) -> a + "; " + b).orElse("Shopify return error");
            throw new AppException(ErrorCode.INVALID_REQUEST, message);
        }
        return object(payload.get("return"));
    }

    private Map<String, Object> data(Map<String, Object> response) {
        List<Map<String, Object>> errors = maps(response.get("errors"));
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Shopify GraphQL error: " + errors);
        }
        return object(response.get("data"));
    }

    private String metadataText(Channel channel, String key) {
        Object value = channel.getMetadata() == null ? null : channel.getMetadata().get(key);
        return value == null || value.toString().isBlank() ? null : value.toString();
    }

    private Map<String, Object> object(Object value) {
        if (!(value instanceof Map<?, ?> source)) return new LinkedHashMap<>();
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private List<Map<String, Object>> connectionNodes(Map<String, Object> connection) {
        Object nodes = connection.get("nodes");
        if (nodes instanceof List<?>) {
            return maps(nodes);
        }
        return maps(connection.get("edges")).stream()
                .map(edge -> object(edge.get("node")))
                .toList();
    }

    private String text(Object value) {
        return value == null || value.toString().isBlank() ? null : value.toString();
    }

    private int integer(Object value) {
        try {
            return value == null ? 0 : Integer.parseInt(value.toString());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private int positive(Integer value) {
        return value == null ? 0 : Math.max(value, 0);
    }

    private List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof List<?> values)) return List.of();
        return values.stream().filter(Map.class::isInstance).map(this::object).toList();
    }
}
