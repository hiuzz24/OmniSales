package fu.osms.channel.dto.request;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
public class CreateManualChannelRequest extends ChannelRequest {
    private String accessToken;
    private String refreshToken;
}
