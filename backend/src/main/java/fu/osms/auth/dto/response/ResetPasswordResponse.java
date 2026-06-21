package fu.osms.auth.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResetPasswordResponse {
    private boolean success;
    private String message;
    private String email;
    private String tempPassword;

    public ResetPasswordResponse(boolean success, String message, String email) {
        this.success = success;
        this.message = message;
        this.email = email;
    }
}
