package fu.osms.sync.inventory;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.inventory-reconciliation")
public class InventoryReconciliationProperties {

    private boolean enabled = true;
    private long fixedDelayMs = 5_000;
    private long debounceMs = 10_000;
    private long verificationDelayMs = 10_000;
    private long processingTimeoutMs = 120_000;
    private int batchSize = 100;
    private int maxApiAttempts = 3;

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

    public long getDebounceMs() {
        return debounceMs;
    }

    public void setDebounceMs(long debounceMs) {
        this.debounceMs = debounceMs;
    }

    public long getVerificationDelayMs() {
        return verificationDelayMs;
    }

    public void setVerificationDelayMs(long verificationDelayMs) {
        this.verificationDelayMs = verificationDelayMs;
    }

    public long getProcessingTimeoutMs() {
        return processingTimeoutMs;
    }

    public void setProcessingTimeoutMs(long processingTimeoutMs) {
        this.processingTimeoutMs = processingTimeoutMs;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getMaxApiAttempts() {
        return maxApiAttempts;
    }

    public void setMaxApiAttempts(int maxApiAttempts) {
        this.maxApiAttempts = maxApiAttempts;
    }
}
