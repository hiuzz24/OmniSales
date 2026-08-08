package fu.osms.sync.ratelimit;

import fu.osms.common.enums.PlatformType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process token bucket limiter, one bucket per marketplace platform.
 * The shared marketplace HTTP client acquires a token before every request so
 * independent flows (order pull, remote import, inventory push) never exceed a
 * platform's rate limit together.
 */
@Component
public class MarketplaceRateLimiter {

    private final RateLimitProperties properties;
    private final Map<PlatformType, TokenBucket> buckets = new ConcurrentHashMap<>();

    public MarketplaceRateLimiter(RateLimitProperties properties) {
        this.properties = properties;
    }

    public void acquire(PlatformType platform) {
        if (!properties.isEnabled() || platform == null) {
            return;
        }
        TokenBucket bucket = buckets.computeIfAbsent(platform, this::newBucket);
        bucket.acquire();
    }

    private TokenBucket newBucket(PlatformType platform) {
        return switch (platform) {
            case LAZADA -> TokenBucket.perMinute(properties.getLazadaRequestsPerMinute());
            case TIKTOK -> TokenBucket.perMinute(properties.getTiktokRequestsPerMinute());
            case SHOPIFY -> TokenBucket.perMinute(properties.getShopifyRequestsPerMinute());
            default -> TokenBucket.perMinute(Integer.MAX_VALUE);
        };
    }

    private static final class TokenBucket {

        private final double refillRatePerMs;
        private final double capacity;
        private double tokens;
        private long lastRefill;

        private TokenBucket(double refillRatePerMs, double capacity) {
            this.refillRatePerMs = refillRatePerMs;
            this.capacity = capacity;
            this.tokens = capacity;
            this.lastRefill = System.currentTimeMillis();
        }

        static TokenBucket perMinute(int requestsPerMinute) {
            if (requestsPerMinute <= 0) {
                throw new IllegalArgumentException("requestsPerMinute must be positive");
            }
            return new TokenBucket(requestsPerMinute / 60_000.0, requestsPerMinute);
        }

        synchronized void acquire() {
            long now = System.currentTimeMillis();
            refill(now);
            if (tokens < 1) {
                long waitMs = (long) Math.ceil((1 - tokens) / refillRatePerMs);
                try {
                    Thread.sleep(waitMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Rate limiter interrupted", e);
                }
                refill(System.currentTimeMillis());
            }
            tokens -= 1;
        }

        private void refill(long now) {
            double elapsed = now - lastRefill;
            tokens = Math.min(capacity, tokens + elapsed * refillRatePerMs);
            lastRefill = now;
        }
    }
}
