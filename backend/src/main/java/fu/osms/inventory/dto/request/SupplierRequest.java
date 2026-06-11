package fu.osms.inventory.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupplierRequest {

    @NotNull(message = "Shop ID must not be null")

    @NotBlank(message = "Supplier name must not be blank")
    @Size(max = 255)
    private String name;

    @Size(max = 255)
    private String contactName;

    @Size(max = 50)
    private String phone;

    @Email(message = "Email is not valid")
    @Size(max = 255)
    private String email;

    private String address;

    @Builder.Default
    private Boolean isActive = true;
}
