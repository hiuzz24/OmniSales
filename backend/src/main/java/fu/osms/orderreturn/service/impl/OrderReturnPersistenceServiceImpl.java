package fu.osms.orderreturn.service.impl;

import fu.osms.channel.entity.Channel;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.order.entity.Order;
import fu.osms.order.entity.OrderItem;
import fu.osms.order.repository.OrderItemRepository;
import fu.osms.order.repository.OrderRepository;
import fu.osms.orderreturn.entity.OrderReturn;
import fu.osms.orderreturn.entity.OrderReturnItem;
import fu.osms.orderreturn.enums.OrderReturnStatus;
import fu.osms.orderreturn.enums.ReturnDataValidationState;
import fu.osms.orderreturn.event.OrderReturnChangedEvent;
import fu.osms.orderreturn.model.OrderReturnSnapshot;
import fu.osms.orderreturn.repository.OrderReturnItemRepository;
import fu.osms.orderreturn.repository.OrderReturnRepository;
import fu.osms.orderreturn.service.OrderReturnPersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderReturnPersistenceServiceImpl implements OrderReturnPersistenceService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderReturnRepository returnRepository;
    private final OrderReturnItemRepository returnItemRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public Optional<UUID> upsert(Channel channel, OrderReturnSnapshot snapshot) {
        if (snapshot.refundOnly()) {
            log.info("[UNSUPPORTED_REFUND_ONLY] platform={} channelId={} externalReturnId={}",
                    channel.getPlatform(), channel.getId(), snapshot.externalReturnId());
            return Optional.empty();
        }
        requireText(snapshot.externalReturnId(), "Return is missing external return id");
        requireText(snapshot.externalOrderId(), "Return is missing external order id");

        Order order = orderRepository.findForUpdateByChannelIdAndExternalOrderId(
                        channel.getId(), snapshot.externalOrderId())
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND,
                        "Order was not found for external id " + snapshot.externalOrderId()));

        OrderReturn orderReturn = returnRepository.findForUpdateByChannelIdAndExternalReturnId(
                        channel.getId(), snapshot.externalReturnId())
                .orElseGet(() -> newReturn(channel, order, snapshot));

        if (isDuplicate(orderReturn, snapshot) || isStale(orderReturn, snapshot)) {
            return Optional.ofNullable(orderReturn.getId());
        }

        mergeHeader(orderReturn, snapshot);
        orderReturn = returnRepository.save(orderReturn);

        List<OrderItem> orderItems = orderItemRepository.findByOrderId(order.getId());
        Map<String, OrderReturnItem> existingItems = returnItemRepository.findByReturnIdWithDetails(orderReturn.getId())
                .stream()
                .collect(Collectors.toMap(OrderReturnItem::getExternalIdentityKey, Function.identity(),
                        (first, ignored) -> first));
        MappingResult mapping = mapItems(orderReturn, orderItems, snapshot.items(), existingItems);
        if (mapping.error() == null) {
            Set<String> incomingIdentities = mapping.items().stream()
                    .map(OrderReturnItem::getExternalIdentityKey)
                    .collect(Collectors.toSet());
            List<OrderReturnItem> removedItems = existingItems.entrySet().stream()
                    .filter(entry -> !incomingIdentities.contains(entry.getKey()))
                    .map(Map.Entry::getValue)
                    .toList();
            if (!removedItems.isEmpty()) {
                returnItemRepository.deleteAll(removedItems);
            }
            returnItemRepository.saveAll(mapping.items());
        }

        String validationError = mapping.error();
        if (validationError == null) {
            validationError = cumulativeValidationError(order, orderReturn, mapping.items(), orderItems);
        }
        if (validationError != null) {
            orderReturn.setDataValidationState(ReturnDataValidationState.INVALID);
            orderReturn.setStatus(OrderReturnStatus.FAILED);
            orderReturn.setLastSyncError(validationError);
        } else {
            orderReturn.setDataValidationState(ReturnDataValidationState.VALID);
            orderReturn.setLastSyncError(null);
            applyTransition(orderReturn, snapshot.status());
        }
        if (snapshot.refundConfirmed()) {
            orderReturn.setRefundConfirmedAt(OffsetDateTime.now());
        }
        OrderReturn saved = returnRepository.save(orderReturn);
        eventPublisher.publishEvent(new OrderReturnChangedEvent(saved.getId()));
        return Optional.of(saved.getId());
    }

    private OrderReturn newReturn(Channel channel, Order order, OrderReturnSnapshot snapshot) {
        return OrderReturn.builder()
                .order(order)
                .channel(channel)
                .platform(channel.getPlatform())
                .externalReturnId(snapshot.externalReturnId())
                .status(snapshot.status() == null ? OrderReturnStatus.PENDING_APPROVAL : snapshot.status())
                .metadata(snapshot.metadata())
                .build();
    }

    private void mergeHeader(OrderReturn target, OrderReturnSnapshot snapshot) {
        target.setPlatformStatus(snapshot.platformStatus());
        target.setPlatformUpdatedAt(snapshot.platformUpdatedAt());
        target.setLastWebhookEventId(trimToNull(snapshot.webhookEventId()));
        if (snapshot.metadata() != null && !snapshot.metadata().isEmpty()) {
            Map<String, Object> metadata = target.getMetadata() == null
                    ? new LinkedHashMap<>()
                    : new LinkedHashMap<>(target.getMetadata());
            metadata.putAll(snapshot.metadata());
            target.setMetadata(metadata);
        }
    }

    private MappingResult mapItems(OrderReturn orderReturn,
                                   List<OrderItem> orderItems,
                                   List<OrderReturnSnapshot.Item> incoming,
                                   Map<String, OrderReturnItem> existingItems) {
        if (incoming == null || incoming.isEmpty()) {
            return new MappingResult(List.of(), "RETURN_ITEMS_MISSING");
        }
        Map<String, OrderItem> byExternalId = orderItems.stream()
                .filter(item -> hasText(item.getExternalItemId()))
                .collect(Collectors.toMap(OrderItem::getExternalItemId, Function.identity(), (first, ignored) -> first));
        Map<String, List<OrderItem>> bySku = orderItems.stream()
                .filter(item -> hasText(item.getSku()))
                .collect(Collectors.groupingBy(item -> item.getSku().toLowerCase(Locale.ROOT)));
        List<ResolvedReturnItem> resolved = new ArrayList<>();
        Set<String> identities = new HashSet<>();
        for (OrderReturnSnapshot.Item source : incoming) {
            String identity = identity(source.externalReturnItemId(), source.externalOrderItemId());
            if (identity == null) {
                return new MappingResult(List.of(), "RETURN_ITEM_IDENTITY_MISSING");
            }
            if (!identities.add(identity)) {
                return new MappingResult(List.of(), "RETURN_ITEM_IDENTITY_DUPLICATED: " + identity);
            }
            OrderItem orderItem = byExternalId.get(source.externalOrderItemId());
            if (orderItem == null && hasText(source.sku())) {
                List<OrderItem> candidates = bySku.getOrDefault(source.sku().toLowerCase(Locale.ROOT), List.of());
                if (candidates.size() == 1) {
                    orderItem = candidates.get(0);
                }
            }
            if (orderItem == null) {
                return new MappingResult(List.of(), "RETURN_ORDER_ITEM_NOT_FOUND: " + identity);
            }
            resolved.add(new ResolvedReturnItem(identity, source, orderItem, existingItems.get(identity)));
        }

        List<OrderReturnItem> mapped = resolved.stream().map(value -> {
            OrderReturnSnapshot.Item source = value.source();
            OrderItem orderItem = value.orderItem();
            OrderReturnItem target = value.existing() == null
                    ? OrderReturnItem.builder()
                            .orderReturn(orderReturn)
                            .externalIdentityKey(value.identity())
                            .build()
                    : value.existing();
            target.setOrderItem(orderItem);
            target.setVariant(orderItem.getVariant());
            target.setExternalOrderItemId(source.externalOrderItemId());
            target.setExternalReturnItemId(source.externalReturnItemId());
            target.setRequestedQuantity(nonNegative(source.requestedQuantity()));
            target.setApprovedQuantity(nonNegative(source.approvedQuantity()));
            target.setRefundedQuantity(source.refundedQuantity() == null
                    ? target.getRefundedQuantity()
                    : nonNegative(source.refundedQuantity()));
            target.setSnapshotSku(orderItem.getSku());
            target.setSnapshotName(hasText(source.name()) ? source.name() : orderItem.getName());
            target.setSnapshotUnitPrice(orderItem.getUnitPrice());
            target.setSnapshotCostPrice(orderItem.getCostPrice());
            return target;
        }).toList();
        return new MappingResult(mapped, null);
    }

    private String cumulativeValidationError(Order order,
                                             OrderReturn current,
                                             List<OrderReturnItem> incoming,
                                             List<OrderItem> orderItems) {
        Map<UUID, Integer> returned = new HashMap<>();
        returnItemRepository.findValidItemsByOrderId(order.getId()).stream()
                .filter(item -> !item.getOrderReturn().getId().equals(current.getId()))
                .filter(item -> item.getOrderItem() != null)
                .forEach(item -> returned.merge(item.getOrderItem().getId(), item.getApprovedQuantity(), Integer::sum));
        incoming.forEach(item -> returned.merge(item.getOrderItem().getId(), item.getApprovedQuantity(), Integer::sum));
        for (OrderItem orderItem : orderItems) {
            if (returned.getOrDefault(orderItem.getId(), 0) > orderItem.getQuantity()) {
                return "RETURN_QUANTITY_EXCEEDS_ORDER_ITEM: " + fallback(orderItem.getSku(), orderItem.getId().toString());
            }
        }
        return null;
    }

    private void applyTransition(OrderReturn target, OrderReturnStatus incoming) {
        if (incoming == null || incoming == target.getStatus() || isTerminal(target.getStatus())) {
            return;
        }
        if (rank(incoming) >= rank(target.getStatus()) || incoming == OrderReturnStatus.REJECTED) {
            target.setStatus(incoming);
            if (incoming == OrderReturnStatus.AWAITING_RETURN && target.getApprovedAt() == null) {
                target.setApprovedAt(OffsetDateTime.now());
            }
        }
    }

    private boolean isDuplicate(OrderReturn target, OrderReturnSnapshot snapshot) {
        return hasText(snapshot.webhookEventId())
                && snapshot.webhookEventId().equals(target.getLastWebhookEventId());
    }

    private boolean isStale(OrderReturn target, OrderReturnSnapshot snapshot) {
        return snapshot.platformUpdatedAt() != null
                && target.getPlatformUpdatedAt() != null
                && snapshot.platformUpdatedAt().isBefore(target.getPlatformUpdatedAt());
    }

    private boolean isTerminal(OrderReturnStatus status) {
        return status == OrderReturnStatus.REJECTED || status == OrderReturnStatus.COMPLETED;
    }

    private int rank(OrderReturnStatus status) {
        if (status == null) return 0;
        return switch (status) {
            case PENDING_APPROVAL -> 1;
            case AWAITING_RETURN -> 2;
            case RETURN_IN_TRANSIT -> 3;
            case INSPECTED -> 4;
            case PLATFORM_PROCESSING -> 5;
            case PENDING_STOCK -> 6;
            case COMPLETED -> 7;
            case REJECTED, FAILED -> 8;
        };
    }

    private String identity(String returnItemId, String orderItemId) {
        if (hasText(returnItemId)) return "RETURN:" + returnItemId.trim();
        if (hasText(orderItemId)) return "ORDER:" + orderItemId.trim();
        return null;
    }

    private int nonNegative(int value) {
        return Math.max(value, 0);
    }

    private void requireText(String value, String message) {
        if (!hasText(value)) throw new AppException(ErrorCode.VALIDATION_FAILED, message);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private String fallback(String value, String fallback) {
        return hasText(value) ? value : fallback;
    }

    private record MappingResult(List<OrderReturnItem> items, String error) {
    }

    private record ResolvedReturnItem(
            String identity,
            OrderReturnSnapshot.Item source,
            OrderItem orderItem,
            OrderReturnItem existing
    ) {
    }
}
