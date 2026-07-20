package fu.osms.channel.token.service;

import fu.osms.channel.token.dto.PlatformTokenRefreshResult;

import fu.osms.common.enums.PlatformType;

public interface PlatformTokenRefresher {
    boolean supports(PlatformType platform);

    PlatformTokenRefreshResult refresh(String refreshToken);
}
