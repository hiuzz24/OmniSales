package fu.osms.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserRequest {

    @NotBlank(message = "Email must not be blank")
    @Email(message = "Email is not valid")
    @Size(max = 255)
    private String email;

    @Size(min = 6, max = 255, message = "Password must be between 6 and 255 characters")
    private String password;

    @NotBlank(message = "Full name must not be blank")
    @Size(max = 255)
    private String fullName;

    @Size(max = 20)
    private String phone;

    private String avatarUrl;

    private String role;
    private String status;
}
