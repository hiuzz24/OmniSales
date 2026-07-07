package fu.osms.system.dto;

import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemLogResponse {
    private UUID id;
    private String type;
    private String message;
    private String user;
    private String ip;
    private String details;
    private OffsetDateTime timestamp;
}
