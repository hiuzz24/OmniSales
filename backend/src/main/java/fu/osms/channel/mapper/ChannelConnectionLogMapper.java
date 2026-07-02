package fu.osms.channel.mapper;

import fu.osms.channel.dto.response.ChannelConnectionLogResponse;
import fu.osms.channel.entity.ChannelConnectionLog;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ChannelConnectionLogMapper {

    @Mapping(source = "channel.id", target = "channelId")
    ChannelConnectionLogResponse toResponse(ChannelConnectionLog log);
}
