package fu.osms.messaging.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Controls how sync events are dispatched.
 * <p>
 * {@code broker} publishes to RabbitMQ and falls back to in-process async
 * execution when the broker is unreachable; {@code local} always dispatches
 * in-process (no broker required).
 */
@Component
@ConfigurationProperties(prefix = "app.messaging")
public class MessagingProperties {

    private String mode = "broker";

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public boolean isBrokerMode() {
        return "broker".equalsIgnoreCase(mode);
    }
}
