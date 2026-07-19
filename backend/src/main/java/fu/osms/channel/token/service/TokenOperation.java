package fu.osms.channel.token.service;

import fu.osms.channel.token.dto.AccessTokenContext;

@FunctionalInterface
public interface TokenOperation<T> {
    T execute(AccessTokenContext token);
}
