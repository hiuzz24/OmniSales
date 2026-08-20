package fu.osms.inventory.addressmatching.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddressGroupRequest {

    @NotEmpty(message = "Danh sach dia chi khong duoc rong")
    private List<String> addresses;
}
