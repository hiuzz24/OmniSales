package fu.osms.notification.mapper;

import fu.osms.notification.dto.response.NotificationResponse;
import fu.osms.notification.entity.Notification;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

    @Mapping(target = "userId", source = "user.id")
    NotificationResponse toResponse(Notification notification);
}
