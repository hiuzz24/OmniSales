package fu.osms.channel.mapper;

import fu.osms.channel.dto.response.ChannelProductResponse;
import fu.osms.channel.entity.ChannelProduct;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface ChannelProductMapper {

    @Mapping(target = "channelId", source = "channel.id")
    @Mapping(target = "channelName", source = "channel.displayName")
    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "productName", source = "product.name")
    ChannelProductResponse toResponse(ChannelProduct channelProduct);
}
