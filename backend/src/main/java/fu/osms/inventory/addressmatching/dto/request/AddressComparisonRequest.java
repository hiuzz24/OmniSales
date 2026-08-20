package fu.osms.inventory.addressmatching.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddressComparisonRequest {

    @NotBlank(message = "address1 khong duoc trong")
    private String address1;

    @NotBlank(message = "address2 khong duoc trong")
    private String address2;
}
