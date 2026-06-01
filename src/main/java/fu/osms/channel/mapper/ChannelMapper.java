package fu.osms.channel.mapper;

import fu.osms.channel.dto.request.ChannelRequest;
import fu.osms.channel.dto.response.ChannelResponse;
import fu.osms.channel.entity.Channel;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface ChannelMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "shop", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "lastSyncedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    Channel toEntity(ChannelRequest request);

    @Mapping(target = "shopId", source = "shop.id")
    @Mapping(target = "shopName", source = "shop.name")
    ChannelResponse toResponse(Channel channel);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "shop", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "lastSyncedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    void updateEntityFromRequest(ChannelRequest request, @MappingTarget Channel channel);
}
