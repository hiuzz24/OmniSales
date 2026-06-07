package fu.osms.auth.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenPairDTO {
    private String accessToken;
    private String refreshToken;
    private UserResponse user;
}
