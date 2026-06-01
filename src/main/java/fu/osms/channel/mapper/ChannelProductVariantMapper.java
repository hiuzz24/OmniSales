package fu.osms.channel.mapper;

import fu.osms.channel.dto.response.ChannelProductVariantResponse;
import fu.osms.channel.entity.ChannelProductVariant;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface ChannelProductVariantMapper {

    @Mapping(target = "channelProductId", source = "channelProduct.id")
    @Mapping(target = "variantId", source = "variant.id")
    @Mapping(target = "variantSku", source = "variant.sku")
    ChannelProductVariantResponse toResponse(ChannelProductVariant channelProductVariant);
}
