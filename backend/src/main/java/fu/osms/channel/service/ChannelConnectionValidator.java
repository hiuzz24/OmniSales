package fu.osms.channel.service;

import fu.osms.channel.entity.Channel;

import java.util.UUID;

public interface ChannelConnectionValidator {

    Channel requireConnected(UUID channelId);

    void validateConnected(Channel channel);
}
