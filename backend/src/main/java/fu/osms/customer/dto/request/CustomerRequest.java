package fu.osms.customer.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerRequest {

    @Size(max = 255)
    private String fullName;

    @Size(max = 50)
    private String gender;

    private LocalDate birth;

    @Size(max = 20)
    private String phone;

    @Email
    @Size(max = 255)
    private String email;

    private Map<String, Object> address;

    private String notes;

    private Boolean isActive;
}
