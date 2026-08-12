package fu.osms.sync.lazada.service;

import fu.osms.channel.dto.response.ChannelResponse;

public interface LazadaChannelConnectionService {

    String buildAuthorizationUrl();

    ChannelResponse connect(String authorizationCode);
}
