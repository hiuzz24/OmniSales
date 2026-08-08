package fu.osms.config;

import fu.osms.sync.ratelimit.MarketplaceHttpRateLimitInterceptor;
import fu.osms.sync.ratelimit.MarketplaceRateLimiter;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class AppConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder, MarketplaceRateLimiter rateLimiter) {
        return builder
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(30))
                .additionalInterceptors(new MarketplaceHttpRateLimitInterceptor(rateLimiter))
                .build();
    }
}
