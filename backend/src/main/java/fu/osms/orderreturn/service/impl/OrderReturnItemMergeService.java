package fu.osms.orderreturn.service.impl;

import fu.osms.order.entity.OrderItem;
import fu.osms.orderreturn.entity.OrderReturn;
import fu.osms.orderreturn.entity.OrderReturnItem;
import fu.osms.orderreturn.model.OrderReturnSnapshot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

final class OrderReturnItemMergeService {

    MappingResult map(OrderReturn orderReturn,
                      List<OrderItem> orderItems,
                      List<OrderReturnSnapshot.Item> incoming,
                      Map<String, OrderReturnItem> existingItems) {
        if (incoming == null || incoming.isEmpty()) {
            return new MappingResult(List.of(), "RETURN_ITEMS_MISSING");
        }
        Map<String, OrderItem> byExternalId = orderItems.stream()
                .filter(item -> hasText(item.getExternalItemId()))
                .collect(Collectors.toMap(OrderItem::getExternalItemId, Function.identity(),
                        (first, ignored) -> first));
        Map<String, List<OrderItem>> bySku = orderItems.stream()
                .filter(item -> hasText(item.getSku()))
                .collect(Collectors.groupingBy(item -> item.getSku().toLowerCase(Locale.ROOT)));
        List<ResolvedReturnItem> resolved = new ArrayList<>();
        Set<String> identities = new HashSet<>();
        for (OrderReturnSnapshot.Item source : incoming) {
            String identity = identity(source.externalReturnItemId(), source.externalOrderItemId());
            if (identity == null) return new MappingResult(List.of(), "RETURN_ITEM_IDENTITY_MISSING");
            if (!identities.add(identity)) {
                return new MappingResult(List.of(), "RETURN_ITEM_IDENTITY_DUPLICATED: " + identity);
            }
            OrderItem orderItem = byExternalId.get(source.externalOrderItemId());
            if (orderItem == null && hasText(source.sku())) {
                List<OrderItem> candidates = bySku.getOrDefault(source.sku().toLowerCase(Locale.ROOT), List.of());
                if (candidates.size() == 1) orderItem = candidates.get(0);
            }
            if (orderItem == null) {
                return new MappingResult(List.of(), "RETURN_ORDER_ITEM_NOT_FOUND: " + identity);
            }
            resolved.add(new ResolvedReturnItem(identity, source, orderItem, existingItems.get(identity)));
        }
        List<OrderReturnItem> mapped = resolved.stream().map(value -> merge(orderReturn, value)).toList();
        return new MappingResult(mapped, null);
    }

    private OrderReturnItem merge(OrderReturn orderReturn, ResolvedReturnItem value) {
        OrderReturnSnapshot.Item source = value.source();
        OrderItem orderItem = value.orderItem();
        OrderReturnItem target = value.existing() == null
                ? OrderReturnItem.builder().orderReturn(orderReturn)
                        .externalIdentityKey(value.identity()).build()
                : value.existing();
        target.setOrderItem(orderItem);
        target.setVariant(orderItem.getVariant());
        target.setExternalOrderItemId(source.externalOrderItemId());
        target.setExternalReturnItemId(source.externalReturnItemId());
        target.setRequestedQuantity(nonNegative(source.requestedQuantity()));
        target.setApprovedQuantity(nonNegative(source.approvedQuantity()));
        if (source.refundedQuantity() != null) {
            target.setRefundedQuantity(nonNegative(source.refundedQuantity()));
        }
        target.setSnapshotSku(orderItem.getSku());
        target.setSnapshotName(hasText(source.name()) ? source.name() : orderItem.getName());
        target.setSnapshotUnitPrice(orderItem.getUnitPrice());
        target.setSnapshotCostPrice(orderItem.getCostPrice());
        return target;
    }

    private String identity(String returnItemId, String orderItemId) {
        if (hasText(returnItemId)) return "RETURN:" + returnItemId.trim();
        if (hasText(orderItemId)) return "ORDER:" + orderItemId.trim();
        return null;
    }

    private int nonNegative(int value) { return Math.max(value, 0); }
    private boolean hasText(String value) { return value != null && !value.isBlank(); }

    record MappingResult(List<OrderReturnItem> items, String error) {
    }

    private record ResolvedReturnItem(String identity, OrderReturnSnapshot.Item source,
                                      OrderItem orderItem, OrderReturnItem existing) {
    }
}
