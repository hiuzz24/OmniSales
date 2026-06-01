package fu.osms.channel.dto.request;

import fu.osms.common.enums.PlatformType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChannelRequest {

    @NotNull(message = "Shop ID không được để trống")
    private UUID shopId;

    @NotNull(message = "Platform không được để trống")
    private PlatformType platform;

    @NotBlank(message = "Tên hiển thị không được để trống")
    @Size(max = 100)
    private String displayName;

    @Size(max = 10)
    private String region = "VN";

    private Map<String, Object> metadata;
}
