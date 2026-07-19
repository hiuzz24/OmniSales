package fu.osms.channel.token.exception;

import lombok.Getter;

@Getter
public class TokenRefreshException extends RuntimeException {
    private final boolean reauthorizationRequired;
    private final boolean revoked;

    public TokenRefreshException(String message, boolean reauthorizationRequired, boolean revoked) {
        super(message);
        this.reauthorizationRequired = reauthorizationRequired;
        this.revoked = revoked;
    }

    public TokenRefreshException(String message, Throwable cause, boolean reauthorizationRequired, boolean revoked) {
        super(message, cause);
        this.reauthorizationRequired = reauthorizationRequired;
        this.revoked = revoked;
    }

    public static TokenRefreshException transientFailure(String message, Throwable cause) {
        return new TokenRefreshException(message, cause, false, false);
    }

    public static TokenRefreshException expired(String message) {
        return new TokenRefreshException(message, true, false);
    }

    public static TokenRefreshException revoked(String message) {
        return new TokenRefreshException(message, true, true);
    }
}
