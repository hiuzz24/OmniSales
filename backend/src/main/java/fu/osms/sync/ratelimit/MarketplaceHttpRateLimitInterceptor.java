package fu.osms.sync.ratelimit;

import fu.osms.common.enums.PlatformType;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.util.Locale;

/**
 * Applies the marketplace rate limiter to every request of the shared
 * RestTemplate used by the Lazada/TikTok/Shopify API clients, keyed by the
 * request host. Non-marketplace hosts pass through untouched.
 */
public class MarketplaceHttpRateLimitInterceptor implements ClientHttpRequestInterceptor {

    private final MarketplaceRateLimiter rateLimiter;

    public MarketplaceHttpRateLimitInterceptor(MarketplaceRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        PlatformType platform = platformFor(request.getURI().getHost());
        if (platform != null) {
            rateLimiter.acquire(platform);
        }
        return execution.execute(request, body);
    }

    private PlatformType platformFor(String host) {
        if (host == null) {
            return null;
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        if (normalized.contains("lazada")) {
            return PlatformType.LAZADA;
        }
        if (normalized.contains("tiktok")) {
            return PlatformType.TIKTOK;
        }
        if (normalized.contains("shopify")) {
            return PlatformType.SHOPIFY;
        }
        return null;
    }
}
