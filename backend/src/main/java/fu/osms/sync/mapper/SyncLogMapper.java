package fu.osms.sync.mapper;

import fu.osms.sync.dto.SyncLogResponse;
import fu.osms.sync.entity.SyncLog;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SyncLogMapper {

    @Mapping(source = "channel.id", target = "channelId")
    @Mapping(source = "channel.displayName", target = "channelName")
    @Mapping(source = "channel.platform", target = "platform")
    @Mapping(source = "product.id", target = "productId")
    @Mapping(source = "product.name", target = "productName")
    @Mapping(source = "product.sku", target = "productSku")
    @Mapping(source = "triggeredBy.email", target = "triggeredByEmail")
    SyncLogResponse toResponse(SyncLog syncLog);
}
