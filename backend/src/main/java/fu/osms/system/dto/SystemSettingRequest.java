package fu.osms.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SystemSettingRequest {
    private String key; // Optional: used for batch updates
    
    @NotBlank(message = "Giá trị cấu hình không được để trống")
    private String value;
}
