package fu.osms.sync.mapper;

import fu.osms.sync.dto.WebhookEventResponse;
import fu.osms.sync.entity.WebhookEvent;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface WebhookEventMapper {

    @Mapping(source = "channel.id", target = "channelId")
    @Mapping(source = "channel.displayName", target = "channelName")
    WebhookEventResponse toResponse(WebhookEvent webhookEvent);
}
