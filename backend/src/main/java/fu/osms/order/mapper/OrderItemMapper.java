package fu.osms.order.mapper;

import fu.osms.order.dto.request.OrderItemRequest;
import fu.osms.order.dto.response.OrderItemResponse;
import fu.osms.order.entity.OrderItem;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface OrderItemMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "order", ignore = true)
    @Mapping(target = "variant", ignore = true)
    @Mapping(target = "channelVariant", ignore = true)
    @Mapping(target = "totalPrice", ignore = true)
    @Mapping(target = "costPrice", ignore = true)
    @Mapping(target = "externalItemId", ignore = true)
    OrderItem toEntity(OrderItemRequest request);

    @Mapping(target = "orderId", source = "order.id")
    @Mapping(target = "variantId", source = "variant.id")
    @Mapping(target = "variantName", source = "variant.name")
    @Mapping(target = "channelVariantId", source = "channelVariant.id")
    OrderItemResponse toResponse(OrderItem item);
}
