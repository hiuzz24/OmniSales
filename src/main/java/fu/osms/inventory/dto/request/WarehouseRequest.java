package fu.osms.inventory.dto.request;

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
public class WarehouseRequest {

    @NotNull(message = "Shop ID không được để trống")
    private UUID shopId;

    @NotBlank(message = "Tên kho không được để trống")
    @Size(max = 255)
    private String name;

    private String address;

    private Boolean isActive = true;
}
