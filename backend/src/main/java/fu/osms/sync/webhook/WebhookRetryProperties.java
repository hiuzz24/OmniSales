package fu.osms.sync.webhook;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.webhook-retry")
public class WebhookRetryProperties {

    private boolean enabled = true;
    private long fixedDelayMs = 30_000;
    private long stuckAfterMs = 120_000;
    private int maxRetries = 3;
    private int batchSize = 50;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getFixedDelayMs() {
        return fixedDelayMs;
    }

    public void setFixedDelayMs(long fixedDelayMs) {
        this.fixedDelayMs = fixedDelayMs;
    }

    public long getStuckAfterMs() {
        return stuckAfterMs;
    }

    public void setStuckAfterMs(long stuckAfterMs) {
        this.stuckAfterMs = stuckAfterMs;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }
}
