package fu.osms.order.mapper;

import fu.osms.order.dto.response.OrderResponse;
import fu.osms.order.entity.Order;
import org.mapstruct.*;

@Mapper(componentModel = "spring",
        uses = {OrderItemMapper.class})
public interface OrderMapper {

    @Mapping(target = "channelId", source = "channel.id")
    @Mapping(target = "customerId", source = "customer.id")
    @Mapping(target = "customerName", source = "customer.fullName")
    @Mapping(target = "cancelledById", source = "cancelledBy.id")
    @Mapping(target = "cancelledByName", source = "cancelledBy.fullName")
    @Mapping(target = "items", ignore = true)
    OrderResponse toResponse(Order order);

    @Mapping(target = "channelId", source = "order.channel.id")
    @Mapping(target = "customerId", source = "order.customer.id")
    @Mapping(target = "customerName", source = "order.customer.fullName")
    @Mapping(target = "cancelledById", source = "order.cancelledBy.id")
    @Mapping(target = "cancelledByName", source = "order.cancelledBy.fullName")
    @Mapping(target = "items", ignore = true)
    OrderResponse toResponseWithItems(Order order);

}
