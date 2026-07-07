package fu.osms.system.service.impl;

import fu.osms.system.dto.ApiEndpointMetricDTO;
import fu.osms.system.dto.ApiTrafficMetricDTO;
import fu.osms.system.dto.ApiUsageSummaryDTO;
import fu.osms.system.entity.ApiEndpointLimit;
import fu.osms.system.entity.ApiMetricDaily;
import fu.osms.system.repository.ApiEndpointLimitRepository;
import fu.osms.system.repository.ApiMetricDailyRepository;
import fu.osms.system.service.ApiMonitorService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiMonitorServiceImpl implements ApiMonitorService {

    private final ApiMetricDailyRepository apiMetricDailyRepository;
    private final ApiEndpointLimitRepository apiEndpointLimitRepository;

    // Buffer to aggregate requests in memory before flushing to Database
    private final ConcurrentHashMap<String, MetricAccumulator> metricsBuffer = new ConcurrentHashMap<>();

    // Real-time rate limiter sliding window: key = "identifier:endpoint", value = RateLimitState
    private final ConcurrentHashMap<String, RateLimitState> rateLimitCache = new ConcurrentHashMap<>();

    // Cached endpoint limit configurations to avoid DB hits on every request
    private final ConcurrentHashMap<String, ApiEndpointLimit> endpointLimitConfigCache = new ConcurrentHashMap<>();
    private long lastConfigLoadTime = 0;
    private static final long CONFIG_CACHE_TTL_MS = 60000; // 1 minute

    @Data
    @AllArgsConstructor
    private static class MetricAccumulator {
        private final AtomicLong requestCount = new AtomicLong(0);
        private final AtomicLong successCount = new AtomicLong(0);
        private final AtomicLong failCount = new AtomicLong(0);
        private final AtomicLong totalLatencyMs = new AtomicLong(0);
    }

    @Data
    private static class RateLimitState {
        private long windowStartMs;
        private int count;

        public RateLimitState(long windowStartMs) {
            this.windowStartMs = windowStartMs;
            this.count = 0;
        }
    }

    @Override
    public void recordRequest(String endpoint, String method, boolean success, long latencyMs) {
        LocalDate today = LocalDate.now();
        int hour = LocalTime.now().getHour();

        // Key format: endpoint#method#date#hour
        String bufferKey = String.format("%s#%s#%s#%d", endpoint, method, today.toString(), hour);

        MetricAccumulator accumulator = metricsBuffer.computeIfAbsent(bufferKey, k -> new MetricAccumulator());
        accumulator.getRequestCount().incrementAndGet();
        if (success) {
            accumulator.getSuccessCount().incrementAndGet();
        } else {
            accumulator.getFailCount().incrementAndGet();
        }
        accumulator.getTotalLatencyMs().addAndGet(latencyMs);
    }

    @Override
    public boolean isRateLimited(String identifier, String endpoint) {
        ApiEndpointLimit limitConfig = getEndpointLimitConfig(endpoint);
        int maxPerMin = limitConfig != null ? limitConfig.getRateLimitPerMin() : 100; // default 100 req/min

        long now = System.currentTimeMillis();
        String limitKey = identifier + ":" + endpoint;

        RateLimitState state = rateLimitCache.compute(limitKey, (key, currentVal) -> {
            if (currentVal == null || (now - currentVal.getWindowStartMs()) > 60000) {
                RateLimitState newVal = new RateLimitState(now);
                newVal.setCount(1);
                return newVal;
            } else {
                currentVal.setCount(currentVal.getCount() + 1);
                return currentVal;
            }
        });

        return state.getCount() > maxPerMin;
    }

    private ApiEndpointLimit getEndpointLimitConfig(String endpoint) {
        long now = System.currentTimeMillis();
        if (now - lastConfigLoadTime > CONFIG_CACHE_TTL_MS) {
            endpointLimitConfigCache.clear();
            apiEndpointLimitRepository.findAll().forEach(limit -> 
                endpointLimitConfigCache.put(limit.getEndpoint(), limit)
            );
            lastConfigLoadTime = now;
        }
        return endpointLimitConfigCache.get(endpoint);
    }

    @Scheduled(fixedDelay = 15000)
    @Transactional
    public void flushMetricsBufferToDatabase() {
        if (metricsBuffer.isEmpty()) {
            return;
        }

        log.debug("Flushing API metrics buffer of size {} to database...", metricsBuffer.size());
        
        // Take a snapshot and clear the active buffer to avoid blocking incoming requests
        Map<String, MetricAccumulator> snapshot = new HashMap<>();
        metricsBuffer.forEach((key, accumulator) -> {
            // Only capture if it has recorded requests
            if (accumulator.getRequestCount().get() > 0) {
                snapshot.put(key, accumulator);
            }
        });
        
        // Clear snapshot keys from the active map
        snapshot.keySet().forEach(metricsBuffer::remove);

        for (Map.Entry<String, MetricAccumulator> entry : snapshot.entrySet()) {
            String[] parts = entry.getKey().split("#");
            if (parts.length < 4) continue;

            String endpoint = parts[0];
            String method = parts[1];
            LocalDate date = LocalDate.parse(parts[2]);
            int hour = Integer.parseInt(parts[3]);

            MetricAccumulator buffer = entry.getValue();
            long newRequests = buffer.getRequestCount().get();
            long newSuccess = buffer.getSuccessCount().get();
            long newFail = buffer.getFailCount().get();
            long newLatencySum = buffer.getTotalLatencyMs().get();

            Optional<ApiMetricDaily> existingMetric = apiMetricDailyRepository
                    .findByEndpointAndMethodAndRecordedDateAndRecordedHour(endpoint, method, date, hour);

            if (existingMetric.isPresent()) {
                ApiMetricDaily metric = existingMetric.get();
                long totalRequests = metric.getRequestCount() + newRequests;
                double currentAvgLatency = metric.getAvgLatencyMs();
                
                // Calculate new running average latency
                double newAvgLatency = ((currentAvgLatency * metric.getRequestCount()) + newLatencySum) / totalRequests;

                metric.setRequestCount(totalRequests);
                metric.setSuccessCount(metric.getSuccessCount() + newSuccess);
                metric.setFailCount(metric.getFailCount() + newFail);
                metric.setAvgLatencyMs(newAvgLatency);
                apiMetricDailyRepository.save(metric);
            } else {
                double avgLatency = newRequests > 0 ? (double) newLatencySum / newRequests : 0.0;
                ApiMetricDaily metric = ApiMetricDaily.builder()
                        .endpoint(endpoint)
                        .method(method)
                        .requestCount(newRequests)
                        .successCount(newSuccess)
                        .failCount(newFail)
                        .avgLatencyMs(avgLatency)
                        .recordedDate(date)
                        .recordedHour(hour)
                        .build();
                apiMetricDailyRepository.save(metric);
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ApiUsageSummaryDTO getSummaryMetrics() {
        LocalDate today = LocalDate.now();
        List<ApiMetricDaily> todayMetrics = apiMetricDailyRepository.findByRecordedDate(today);

        long totalRequests = 0;
        long totalSuccess = 0;
        long totalFail = 0;
        double sumLatencyWeighted = 0.0;

        for (ApiMetricDaily m : todayMetrics) {
            totalRequests += m.getRequestCount();
            totalSuccess += m.getSuccessCount();
            totalFail += m.getFailCount();
            sumLatencyWeighted += m.getAvgLatencyMs() * m.getRequestCount();
        }

        double successRate = totalRequests > 0 ? ((double) totalSuccess / totalRequests) * 100.0 : 100.0;
        double avgLatency = totalRequests > 0 ? sumLatencyWeighted / totalRequests : 0.0;

        // Count endpoints near their daily quota (e.g. usage > 80%)
        int nearQuotaAlerts = 0;
        List<ApiEndpointLimit> limits = apiEndpointLimitRepository.findAll();
        Map<String, Long> endpointTodayUsage = new HashMap<>();
        for (ApiMetricDaily m : todayMetrics) {
            endpointTodayUsage.put(m.getEndpoint(), endpointTodayUsage.getOrDefault(m.getEndpoint(), 0L) + m.getRequestCount());
        }

        for (ApiEndpointLimit limit : limits) {
            long usage = endpointTodayUsage.getOrDefault(limit.getEndpoint(), 0L);
            if (limit.getDailyQuota() > 0 && ((double) usage / limit.getDailyQuota()) >= 0.8) {
                nearQuotaAlerts++;
            }
        }

        return ApiUsageSummaryDTO.builder()
                .totalRequestsToday(totalRequests)
                .successRate(successRate)
                .avgLatencyMs(avgLatency)
                .activeAlertsCount(nearQuotaAlerts)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApiTrafficMetricDTO> getTrafficMetrics(String range) {
        LocalDate today = LocalDate.now();
        LocalDate startDate;

        if ("7d".equalsIgnoreCase(range)) {
            startDate = today.minusDays(7);
        } else if ("24h".equalsIgnoreCase(range)) {
            startDate = today.minusDays(1);
        } else { // default "today"
            startDate = today;
        }

        List<ApiMetricDaily> rawMetrics = apiMetricDailyRepository.findMetricsFromDate(startDate);
        Map<String, ApiTrafficMetricDTO> timeGroupedMap = new LinkedHashMap<>();

        if ("7d".equalsIgnoreCase(range)) {
            // Group by Date: "YYYY-MM-DD"
            for (ApiMetricDaily m : rawMetrics) {
                String label = m.getRecordedDate().toString();
                timeGroupedMap.computeIfAbsent(label, k -> new ApiTrafficMetricDTO(label, 0L, 0L, 0L));
                ApiTrafficMetricDTO dto = timeGroupedMap.get(label);
                dto.setRequestCount(dto.getRequestCount() + m.getRequestCount());
                dto.setSuccessCount(dto.getSuccessCount() + m.getSuccessCount());
                dto.setFailCount(dto.getFailCount() + m.getFailCount());
            }
        } else if ("24h".equalsIgnoreCase(range)) {
            // Group by Date + Hour: "MM-DD HH:00"
            for (ApiMetricDaily m : rawMetrics) {
                String label = String.format("%02d-%02d %02d:00", 
                        m.getRecordedDate().getMonthValue(), 
                        m.getRecordedDate().getDayOfMonth(), 
                        m.getRecordedHour());
                timeGroupedMap.computeIfAbsent(label, k -> new ApiTrafficMetricDTO(label, 0L, 0L, 0L));
                ApiTrafficMetricDTO dto = timeGroupedMap.get(label);
                dto.setRequestCount(dto.getRequestCount() + m.getRequestCount());
                dto.setSuccessCount(dto.getSuccessCount() + m.getSuccessCount());
                dto.setFailCount(dto.getFailCount() + m.getFailCount());
            }
        } else {
            // Group by Hour for "today": "HH:00"
            // Initialize all 24 hours of today for a cleaner chart
            for (int h = 0; h < 24; h++) {
                String label = String.format("%02d:00", h);
                timeGroupedMap.put(label, new ApiTrafficMetricDTO(label, 0L, 0L, 0L));
            }
            for (ApiMetricDaily m : rawMetrics) {
                if (m.getRecordedDate().equals(today)) {
                    String label = String.format("%02d:00", m.getRecordedHour());
                    ApiTrafficMetricDTO dto = timeGroupedMap.get(label);
                    if (dto != null) {
                        dto.setRequestCount(dto.getRequestCount() + m.getRequestCount());
                        dto.setSuccessCount(dto.getSuccessCount() + m.getSuccessCount());
                        dto.setFailCount(dto.getFailCount() + m.getFailCount());
                    }
                }
            }
        }

        return new ArrayList<>(timeGroupedMap.values());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApiEndpointMetricDTO> getEndpointMetrics() {
        LocalDate today = LocalDate.now();
        List<ApiMetricDaily> todayMetrics = apiMetricDailyRepository.findByRecordedDate(today);

        // Group today's hourly records into summary per endpoint+method
        Map<String, ApiEndpointMetricDTO> summaryMap = new HashMap<>();

        for (ApiMetricDaily m : todayMetrics) {
            String key = m.getEndpoint() + "#" + m.getMethod();
            summaryMap.computeIfAbsent(key, k -> {
                ApiEndpointLimit limit = getEndpointLimitConfig(m.getEndpoint());
                int rateLimit = limit != null ? limit.getRateLimitPerMin() : 100;
                int dailyQuota = limit != null ? limit.getDailyQuota() : 50000;
                return ApiEndpointMetricDTO.builder()
                        .endpoint(m.getEndpoint())
                        .method(m.getMethod())
                        .requestCount(0L)
                        .successCount(0L)
                        .failCount(0L)
                        .avgLatencyMs(0.0)
                        .rateLimitPerMin(rateLimit)
                        .dailyQuota(dailyQuota)
                        .remainingQuota((long) dailyQuota)
                        .build();
            });

            ApiEndpointMetricDTO dto = summaryMap.get(key);
            long oldRequests = dto.getRequestCount();
            long newRequests = m.getRequestCount();
            long totalRequests = oldRequests + newRequests;
            
            double currentAvg = dto.getAvgLatencyMs();
            double avgLatency = totalRequests > 0 ? 
                    ((currentAvg * oldRequests) + (m.getAvgLatencyMs() * newRequests)) / totalRequests : 0.0;

            dto.setRequestCount(totalRequests);
            dto.setSuccessCount(dto.getSuccessCount() + m.getSuccessCount());
            dto.setFailCount(dto.getFailCount() + m.getFailCount());
            dto.setAvgLatencyMs(avgLatency);
            dto.setRemainingQuota(Math.max(0L, dto.getDailyQuota() - totalRequests));
        }

        // Add configured endpoints that had 0 requests today
        List<ApiEndpointLimit> allLimits = apiEndpointLimitRepository.findAll();
        for (ApiEndpointLimit limit : allLimits) {
            String getSecKey = limit.getEndpoint() + "#GET";
            String postSecKey = limit.getEndpoint() + "#POST";
            // Check if this endpoint was queried today. If not, add empty metrics for GET/POST
            if (!summaryMap.containsKey(getSecKey) && !summaryMap.containsKey(postSecKey) && 
                !summaryMap.keySet().stream().anyMatch(k -> k.startsWith(limit.getEndpoint() + "#"))) {
                
                summaryMap.put(getSecKey, ApiEndpointMetricDTO.builder()
                        .endpoint(limit.getEndpoint())
                        .method("GET")
                        .requestCount(0L)
                        .successCount(0L)
                        .failCount(0L)
                        .avgLatencyMs(0.0)
                        .rateLimitPerMin(limit.getRateLimitPerMin())
                        .dailyQuota(limit.getDailyQuota())
                        .remainingQuota((long) limit.getDailyQuota())
                        .build());
            }
        }

        List<ApiEndpointMetricDTO> results = new ArrayList<>(summaryMap.values());
        results.sort(Comparator.comparing(ApiEndpointMetricDTO::getRequestCount).reversed());
        return results;
    }
}
