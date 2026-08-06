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
        processCommandFactory().validateFullReceipt(context);
        Map<String, Object> detail = getReturn(channelId, returnGid);
        boolean hasRestockableItems = context.items().stream()
                .anyMatch(item -> positive(item.restockableQuantity()) > 0);
        String locationId = hasRestockableItems
                ? inventoryGateway.resolveManagedLocationId(channelId)
                : null;
        Map<String, Object> input = processCommandFactory().build(
                returnGid,
                context,
                detail,
                locationId,
                suggestedRefundPlan(channelId, returnGid, context)
        );
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

    private ShopifyRefundPlan suggestedRefundPlan(UUID channelId,
                                                   String returnGid,
                                                   ReturnActionContext context) {
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
        ShopifyRefundPlanResolver resolver = refundPlanResolver();
        Map<String, Object> response = execute(channelId, query, Map.of(
                "id", returnGid,
                "returnLineItems", resolver.requestedLines(context)));
        return resolver.resolve(data(response));
    }

    private ShopifyReturnProcessCommandFactory processCommandFactory() {
        return new ShopifyReturnProcessCommandFactory();
    }

    private ShopifyRefundPlanResolver refundPlanResolver() {
        return new ShopifyRefundPlanResolver();
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
