package fu.osms.order.mapper;

import fu.osms.order.dto.request.OrderRequest;
import fu.osms.order.dto.response.OrderResponse;
import fu.osms.order.entity.Order;
import org.mapstruct.*;

@Mapper(componentModel = "spring",
        uses = {OrderItemMapper.class})
public interface OrderMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "shop", ignore = true)
    @Mapping(target = "channel", ignore = true)
    @Mapping(target = "statusChangedAt", ignore = true)
    @Mapping(target = "totalAmount", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "cancelledBy", ignore = true)
    @Mapping(target = "cancelReason", ignore = true)
    Order toEntity(OrderRequest request);

    @Mapping(target = "shopId", source = "shop.id")
    @Mapping(target = "shopName", source = "shop.name")
    @Mapping(target = "channelId", source = "channel.id")
    @Mapping(target = "cancelledById", source = "cancelledBy.id")
    @Mapping(target = "cancelledByName", source = "cancelledBy.fullName")
    @Mapping(target = "items", ignore = true)
    OrderResponse toResponse(Order order);

    /**
     * Phiên bản đầy đủ: bao gồm danh sách items.
     */
    @Mapping(target = "shopId", source = "order.shop.id")
    @Mapping(target = "shopName", source = "order.shop.name")
    @Mapping(target = "channelId", source = "order.channel.id")
    @Mapping(target = "cancelledById", source = "order.cancelledBy.id")
    @Mapping(target = "cancelledByName", source = "order.cancelledBy.fullName")
    OrderResponse toResponseWithItems(Order order);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "shop", ignore = true)
    @Mapping(target = "channel", ignore = true)
    @Mapping(target = "statusChangedAt", ignore = true)
    @Mapping(target = "totalAmount", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "cancelledBy", ignore = true)
    @Mapping(target = "cancelReason", ignore = true)
    void updateEntityFromRequest(OrderRequest request, @MappingTarget Order order);
}
