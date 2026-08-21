package fu.osms.order.mapper;

import fu.osms.order.dto.response.OrderResponse;
import fu.osms.order.entity.Order;
import org.mapstruct.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mapper(componentModel = "spring",
        uses = {OrderItemMapper.class})
public interface OrderMapper {

    @Mapping(target = "channelId", source = "channel.id")
    @Mapping(target = "customerId", source = "customer.id")
    @Mapping(target = "customerName", source = "customer.fullName")
    @Mapping(target = "cancelledById", source = "cancelledBy.id")
    @Mapping(target = "cancelledByName", source = "cancelledBy.fullName")
    @Mapping(target = "items", ignore = true)
    @Mapping(target = "waitingStockExpired", ignore = true)
    @Mapping(target = "waitingStockItems", ignore = true)
    @Mapping(target = "stockReadyForConfirmation", ignore = true)
    @Mapping(target = "stockReadyAt", ignore = true)
    OrderResponse toResponse(Order order);

    @Mapping(target = "channelId", source = "order.channel.id")
    @Mapping(target = "customerId", source = "order.customer.id")
    @Mapping(target = "customerName", source = "order.customer.fullName")
    @Mapping(target = "cancelledById", source = "order.cancelledBy.id")
    @Mapping(target = "cancelledByName", source = "order.cancelledBy.fullName")
    @Mapping(target = "items", ignore = true)
    @Mapping(target = "waitingStockExpired", ignore = true)
    @Mapping(target = "waitingStockItems", ignore = true)
    @Mapping(target = "stockReadyForConfirmation", ignore = true)
    @Mapping(target = "stockReadyAt", ignore = true)
    OrderResponse toResponseWithItems(Order order);

    @AfterMapping
    default void mapWaitingStock(Order order, @MappingTarget OrderResponse response) {
        response.setWaitingStockExpired(order.getWaitingStockExpiresAt() != null
                && !order.getWaitingStockExpiresAt().isAfter(OffsetDateTime.now()));
        Map<String, Object> waitingStock = waitingStockMetadata(order.getPlatformMetadata());
        response.setStockReadyForConfirmation(Boolean.TRUE.equals(waitingStock.get("stockReadyForConfirmation")));
        response.setStockReadyAt(offsetDateTime(waitingStock.get("stockReadyAt")));
        response.setWaitingStockItems(waitingStockItems(order.getPlatformMetadata()));
    }

    private static Map<String, Object> waitingStockMetadata(Map<String, Object> metadata) {
        if (metadata == null || !(metadata.get("waitingStock") instanceof Map<?, ?> waiting)) {
            return Map.of();
        }
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        waiting.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private static OffsetDateTime offsetDateTime(Object value) {
        if (value == null) return null;
        try {
            return OffsetDateTime.parse(String.valueOf(value));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static List<fu.osms.order.dto.response.WaitingStockItemResponse> waitingStockItems(
            Map<String, Object> metadata) {
        if (metadata == null || !(metadata.get("waitingStock") instanceof Map<?, ?> waitingStock)
                || !(waitingStock.get("items") instanceof List<?> items)) {
            return List.of();
        }
        List<fu.osms.order.dto.response.WaitingStockItemResponse> result = new ArrayList<>();
        for (Object value : items) {
            if (!(value instanceof Map<?, ?> item)) {
                continue;
            }
            result.add(new fu.osms.order.dto.response.WaitingStockItemResponse(
                    uuid(item.get("variantId")),
                    text(item.get("sku")),
                    text(item.get("name")),
                    number(item.get("required")),
                    number(item.get("available")),
                    number(item.get("missing"))
            ));
        }
        return result;
    }

    private static UUID uuid(Object value) {
        try {
            return value == null ? null : UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static int number(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? 0 : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

}
