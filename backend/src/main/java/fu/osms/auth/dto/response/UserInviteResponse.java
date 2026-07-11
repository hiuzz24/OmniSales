package fu.osms.auth.dto.response;

import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserInviteResponse {
    private UUID id;
    private String email;
    private String roleName;
    private String token;
    private OffsetDateTime expiresAt;
    private OffsetDateTime usedAt;
    private OffsetDateTime createdAt;
    private String status; // PENDING, ACCEPTED, CANCELLED, EXPIRED
}
