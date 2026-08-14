package fu.osms.order.mapper;

import fu.osms.order.dto.response.OrderItemResponse;
import fu.osms.order.entity.OrderItem;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface OrderItemMapper {

    @Mapping(target = "orderId", source = "order.id")
    @Mapping(target = "variantId", source = "variant.id")
    @Mapping(target = "variantName", source = "variant.name")
    @Mapping(target = "channelVariantId", source = "channelVariant.id")
    OrderItemResponse toResponse(OrderItem item);
}
