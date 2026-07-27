package fu.osms.sync.inventory;

public class PlatformInventoryException extends RuntimeException {

    private final boolean retryable;

    public PlatformInventoryException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public PlatformInventoryException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
