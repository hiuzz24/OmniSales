package fu.osms.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager platformLookupCacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.registerCustomCache("lazadaCategoryTree", Caffeine.newBuilder()
                .maximumSize(50)
                .expireAfterWrite(Duration.ofHours(6))
                .build());
        manager.registerCustomCache("lazadaCategoryAttributes", Caffeine.newBuilder()
                .maximumSize(300)
                .expireAfterWrite(Duration.ofHours(1))
                .build());
        manager.registerCustomCache("tiktokCategories", Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(Duration.ofHours(1))
                .build());
        manager.registerCustomCache("tiktokCategoryAttributes", Caffeine.newBuilder()
                .maximumSize(300)
                .expireAfterWrite(Duration.ofHours(1))
                .build());
        manager.registerCustomCache("tiktokSuggestionImageUris", Caffeine.newBuilder()
                .maximumSize(300)
                .expireAfterWrite(Duration.ofHours(6))
                .build());
        return manager;
    }
}
