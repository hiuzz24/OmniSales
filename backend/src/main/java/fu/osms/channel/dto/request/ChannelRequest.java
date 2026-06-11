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

    @NotNull(message = "Shop ID must not be null")

    @NotNull(message = "Platform must not be null")
    private PlatformType platform;

    @NotBlank(message = "Display name must not be blank")
    @Size(max = 100)
    private String displayName;

    @Size(max = 10)
    @Builder.Default
    private String region = "VN";

    private Map<String, Object> metadata;

    @Builder.Default
    private Boolean syncEnabled = true;
}
