package fu.osms.sync.order.pull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.order-pull-recovery")
public class OrderPullRecoveryProperties {

    private boolean enabled = true;
    private long fixedDelayMs = 30_000;
    private long publishedStaleMs = 120_000;
    private long processingStaleMs = 1_800_000;
    private int maxAttempts = 3;
    private int batchSize = 20;

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

    public long getPublishedStaleMs() {
        return publishedStaleMs;
    }

    public void setPublishedStaleMs(long publishedStaleMs) {
        this.publishedStaleMs = publishedStaleMs;
    }

    public long getProcessingStaleMs() {
        return processingStaleMs;
    }

    public void setProcessingStaleMs(long processingStaleMs) {
        this.processingStaleMs = processingStaleMs;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }
}
