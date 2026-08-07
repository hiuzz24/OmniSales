package fu.osms.auth.dto.response;

import fu.osms.auth.enums.UserStatus;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {
    private UUID id;
    private String email;
    private String fullName;
    private String phone;
    private String avatarUrl;
    private UserStatus status;
    private String role;
    private OffsetDateTime createdAt;
    private Boolean passwordExpired;
    private UUID warehouseId;
    private String warehouseName;
    private String inviteStatus;
    private OffsetDateTime deletedAt;
}
