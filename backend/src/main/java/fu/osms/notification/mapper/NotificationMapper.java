package fu.osms.notification.mapper;

import fu.osms.notification.dto.response.NotificationResponse;
import fu.osms.notification.entity.Notification;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

    @Mapping(target = "shopId", source = "shop.id")
    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "isRead", expression = "java(notification.getReadAt() != null)")
    NotificationResponse toResponse(Notification notification);
}
