package fu.osms.sync.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;
    private int lazadaRequestsPerMinute = 100;
    private int tiktokRequestsPerMinute = 100;
    private int shopifyRequestsPerMinute = 120;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getLazadaRequestsPerMinute() {
        return lazadaRequestsPerMinute;
    }

    public void setLazadaRequestsPerMinute(int lazadaRequestsPerMinute) {
        this.lazadaRequestsPerMinute = lazadaRequestsPerMinute;
    }

    public int getTiktokRequestsPerMinute() {
        return tiktokRequestsPerMinute;
    }

    public void setTiktokRequestsPerMinute(int tiktokRequestsPerMinute) {
        this.tiktokRequestsPerMinute = tiktokRequestsPerMinute;
    }

    public int getShopifyRequestsPerMinute() {
        return shopifyRequestsPerMinute;
    }

    public void setShopifyRequestsPerMinute(int shopifyRequestsPerMinute) {
        this.shopifyRequestsPerMinute = shopifyRequestsPerMinute;
    }
}
