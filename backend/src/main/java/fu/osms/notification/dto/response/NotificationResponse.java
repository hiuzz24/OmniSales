package fu.osms.notification.dto.response;

import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationResponse {

    private UUID id;
    private UUID userId;
    private String type;
    private String title;
    private String body;
    private OffsetDateTime readAt;
    private String entityType;
    private UUID entityId;
    private OffsetDateTime createdAt;
}
