package fu.osms.channel.token.exception;

public class PlatformAccessTokenExpiredException extends RuntimeException {
    public PlatformAccessTokenExpiredException(String message) {
        super(message);
    }

    public PlatformAccessTokenExpiredException(String message, Throwable cause) {
        super(message, cause);
    }
}
