package fu.osms.orderreturn.service.impl;

import fu.osms.channel.entity.Channel;
import fu.osms.channel.repository.ChannelRepository;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderReturnPersistenceTransactionService {

    private final ChannelRepository channelRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderReturnRepository returnRepository;
    private final OrderReturnItemRepository returnItemRepository;
    private final ApplicationEventPublisher eventPublisher;

    /** Khóa Order rồi Return, chặn dữ liệu cũ/trùng và commit một snapshot platform. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<UUID> upsertOnce(UUID channelId, OrderReturnSnapshot snapshot) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new AppException(ErrorCode.CHANNEL_NOT_FOUND));
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
        boolean isNew = orderReturn.getId() == null;
        mergeHeader(orderReturn, snapshot);
        if (isNew) {
            orderReturn = returnRepository.saveAndFlush(orderReturn);
        }

        List<OrderItem> orderItems = orderItemRepository.findByOrderId(order.getId());
        Map<String, OrderReturnItem> existingItems = returnItemRepository.findByReturnIdWithDetails(orderReturn.getId())
                .stream()
                .collect(Collectors.toMap(OrderReturnItem::getExternalIdentityKey, Function.identity(),
                        (first, ignored) -> first));
        OrderReturnItemMergeService.MappingResult mapping = itemMergeService()
                .map(orderReturn, orderItems, snapshot.items(), existingItems);
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
            validationError = validationPolicy()
                    .cumulativeError(order, orderReturn, mapping.items(), orderItems);
        }
        if (validationError != null) {
            orderReturn.setDataValidationState(ReturnDataValidationState.INVALID);
            orderReturn.setStatus(OrderReturnStatus.FAILED);
            orderReturn.setLastSyncError(validationError);
        } else {
            orderReturn.setDataValidationState(ReturnDataValidationState.VALID);
            orderReturn.setLastSyncError(null);
            transitionPolicy().apply(orderReturn, snapshot.status());
        }
        boolean platformProcessApplied = channel.getPlatform() != fu.osms.common.enums.PlatformType.SHOPIFY
                || "CLOSED".equalsIgnoreCase(snapshot.platformStatus());
        if (platformProcessApplied
                && snapshot.refundConfirmed()
                && validationPolicy().refundCoversReceivedQuantities(mapping.items())) {
            orderReturn.setRefundConfirmedAt(OffsetDateTime.now());
        }
        if (channel.getPlatform() == fu.osms.common.enums.PlatformType.SHOPIFY
                && "CLOSED".equalsIgnoreCase(snapshot.platformStatus())
                && "FAILURE".equals(snapshot.metadata() == null
                        ? null
                        : snapshot.metadata().get("shopifyRefundState"))) {
            orderReturn.setLastSyncError(
                    "Shopify closed the return, but the refund transaction failed. Resolve the refund on Shopify and refresh.");
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

    private OrderReturnItemMergeService itemMergeService() {
        return new OrderReturnItemMergeService();
    }

    private OrderReturnValidationPolicy validationPolicy() {
        return new OrderReturnValidationPolicy(returnItemRepository);
    }

    private OrderReturnTransitionPolicy transitionPolicy() {
        return new OrderReturnTransitionPolicy();
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

    private void requireText(String value, String message) {
        if (!hasText(value)) throw new AppException(ErrorCode.VALIDATION_FAILED, message);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }
}
