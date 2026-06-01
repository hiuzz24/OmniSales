package fu.osms.channel.mapper;

import fu.osms.channel.dto.response.ChannelCredentialResponse;
import fu.osms.channel.entity.ChannelCredential;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface ChannelCredentialMapper {

    /**
     * Chuyển đổi ChannelCredential -> ChannelCredentialResponse.
     * accessToken và refreshToken KHÔNG được map để bảo mật.
     */
    @Mapping(target = "channelId", source = "channel.id")
    @Mapping(target = "channelName", source = "channel.displayName")
    ChannelCredentialResponse toResponse(ChannelCredential credential);
}
