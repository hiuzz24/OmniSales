package fu.osms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configuration for async task execution.
 * Used for async email sending and other background tasks.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // Core pool size - số thread luôn chạy
        executor.setCorePoolSize(2);
        
        // Max pool size - số thread tối đa
        executor.setMaxPoolSize(5);
        
        // Queue capacity - số task trong hàng đợi
        executor.setQueueCapacity(100);
        
        // Thread name prefix
        executor.setThreadNamePrefix("Async-Email-");
        
        // Khởi tạo executor
        executor.initialize();
        
        return executor;
    }
}
