package fu.osms.audit.dto.response;

import lombok.*;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogResponse {

    private UUID id;
    private UUID actorId;
    private String actorEmail;
    private String action;
    private String entityType;
    private UUID entityId;
    private String entityName;
    private Map<String, Object> changes;
    private OffsetDateTime performedAt;
}
