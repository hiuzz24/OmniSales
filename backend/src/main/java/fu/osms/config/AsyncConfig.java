package fu.osms.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Configuration for async task execution.
 * Used for async email sending and other background tasks.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Value("${app.async.order-pull.core-size:2}")
    private int orderPullCoreSize;

    @Value("${app.async.order-pull.max-size:6}")
    private int orderPullMaxSize;

    @Value("${app.async.order-pull.queue-capacity:50}")
    private int orderPullQueueCapacity;

    @Value("${app.async.sync-job.core-size:4}")
    private int syncJobCoreSize;

    @Value("${app.async.sync-job.max-size:8}")
    private int syncJobMaxSize;

    @Value("${app.async.sync-job.queue-capacity:100}")
    private int syncJobQueueCapacity;

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

    @Bean(name = "webhookExecutor")
    public Executor webhookExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("Webhook-");
        // If the queue is temporarily full, preserve the event instead of silently dropping it.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    @Bean(name = "orderPullExecutor")
    public Executor orderPullExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(orderPullCoreSize);
        executor.setMaxPoolSize(orderPullMaxSize);
        executor.setQueueCapacity(orderPullQueueCapacity);
        executor.setThreadNamePrefix("OrderPull-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }

    @Bean(name = "syncJobExecutor")
    public Executor syncJobExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(syncJobCoreSize);
        executor.setMaxPoolSize(syncJobMaxSize);
        executor.setQueueCapacity(syncJobQueueCapacity);
        executor.setThreadNamePrefix("Marketplace-Sync-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
