package fu.osms.system.service.impl;

import fu.osms.system.dto.ApiEndpointMetricDTO;
import fu.osms.system.dto.ApiUsageSummaryDTO;
import fu.osms.system.entity.ApiEndpointLimit;
import fu.osms.system.entity.ApiMetricDaily;
import fu.osms.system.repository.ApiEndpointLimitRepository;
import fu.osms.system.repository.ApiMetricDailyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApiMonitorServiceImpl Tests")
class ApiMonitorServiceImplTest {

    @Mock private ApiMetricDailyRepository apiMetricDailyRepository;
    @Mock private ApiEndpointLimitRepository apiEndpointLimitRepository;

    private ApiMonitorServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ApiMonitorServiceImpl(apiMetricDailyRepository, apiEndpointLimitRepository);
    }

    // ---------- recordRequest + flushMetricsBufferToDatabase ----------

    @Test
    @DisplayName("recordRequest: increments request + success counters when success=true")
    void recordRequest_success_true() {
        service.recordRequest("/api/x", "GET", true, 100);
        service.recordRequest("/api/x", "GET", true, 50);

        when(apiMetricDailyRepository.findByEndpointAndMethodAndRecordedDateAndRecordedHour(
                any(), any(), any(), anyInt())).thenReturn(Optional.empty());
        service.flushMetricsBufferToDatabase();

        ArgumentCaptor<ApiMetricDaily> captor = ArgumentCaptor.forClass(ApiMetricDaily.class);
        verify(apiMetricDailyRepository).save(captor.capture());
        ApiMetricDaily saved = captor.getValue();
        assertThat(saved.getEndpoint()).isEqualTo("/api/x");
        assertThat(saved.getMethod()).isEqualTo("GET");
        assertThat(saved.getRequestCount()).isEqualTo(2L);
        assertThat(saved.getSuccessCount()).isEqualTo(2L);
        assertThat(saved.getFailCount()).isEqualTo(0L);
        assertThat(saved.getAvgLatencyMs()).isEqualTo(75.0);
    }

    @Test
    @DisplayName("recordRequest: increments fail counter when success=false")
    void recordRequest_success_false() {
        service.recordRequest("/api/y", "POST", false, 200);
        when(apiMetricDailyRepository.findByEndpointAndMethodAndRecordedDateAndRecordedHour(
                any(), any(), any(), anyInt())).thenReturn(Optional.empty());
        service.flushMetricsBufferToDatabase();

        ArgumentCaptor<ApiMetricDaily> captor = ArgumentCaptor.forClass(ApiMetricDaily.class);
        verify(apiMetricDailyRepository).save(captor.capture());
        ApiMetricDaily saved = captor.getValue();
        assertThat(saved.getRequestCount()).isEqualTo(1L);
        assertThat(saved.getSuccessCount()).isEqualTo(0L);
        assertThat(saved.getFailCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("flushMetricsBufferToDatabase: updates existing metric with weighted average latency")
    void flush_updatesExistingMetric() {
        service.recordRequest("/api/z", "GET", true, 200);
        ApiMetricDaily existing = ApiMetricDaily.builder()
                .endpoint("/api/z")
                .method("GET")
                .requestCount(10L)
                .successCount(8L)
                .failCount(2L)
                .avgLatencyMs(50.0)
                .recordedDate(LocalDate.now())
                .recordedHour(LocalTime.now().getHour())
                .build();
        when(apiMetricDailyRepository.findByEndpointAndMethodAndRecordedDateAndRecordedHour(
                any(String.class), any(String.class), any(LocalDate.class), anyInt()))
                .thenReturn(Optional.of(existing));
        service.flushMetricsBufferToDatabase();

        // Both branches of the flush call save, but for the existing branch the
        // mutated object IS the same one returned from the repo (verified via captor).
        ArgumentCaptor<ApiMetricDaily> captor = ArgumentCaptor.forClass(ApiMetricDaily.class);
        verify(apiMetricDailyRepository).save(captor.capture());
        assertThat(captor.getValue()).isSameAs(existing);
        assertThat(existing.getRequestCount()).isEqualTo(11L); // 10 + 1
        assertThat(existing.getSuccessCount()).isEqualTo(9L);  // 8 + 1
        assertThat(existing.getFailCount()).isEqualTo(2L);
        // new avg = ((50 * 10) + 200) / 11 = 700 / 11 = ~63.636
        assertThat(existing.getAvgLatencyMs()).isCloseTo(700.0 / 11.0, offset(0.001));
    }

    @Test
    @DisplayName("flushMetricsBufferToDatabase: no-op when buffer is empty")
    void flush_emptyBuffer_doesNothing() {
        service.flushMetricsBufferToDatabase();
        verify(apiMetricDailyRepository, never()).save(any(ApiMetricDaily.class));
    }

    // ---------- isRateLimited ----------

    @Test
    @DisplayName("isRateLimited: always returns false for /api/auth/login (whitelisted)")
    void isRateLimited_loginEndpoint_notLimited() {
        for (int i = 0; i < 1000; i++) {
            assertThat(service.isRateLimited("user-1", "/api/auth/login")).isFalse();
        }
    }

    @Test
    @DisplayName("isRateLimited: uses default 100 req/min when no limit configured")
    void isRateLimited_usesDefaultLimit() {
        when(apiEndpointLimitRepository.findAll()).thenReturn(List.of());
        for (int i = 0; i < 100; i++) {
            assertThat(service.isRateLimited("user-1", "/api/orders")).isFalse();
        }
        // 101st call should be limited.
        assertThat(service.isRateLimited("user-1", "/api/orders")).isTrue();
    }

    @Test
    @DisplayName("isRateLimited: respects configured rateLimitPerMin")
    void isRateLimited_respectsConfig() {
        ApiEndpointLimit limit = ApiEndpointLimit.builder()
                .endpoint("/api/products")
                .rateLimitPerMin(3)
                .dailyQuota(1000)
                .build();
        when(apiEndpointLimitRepository.findAll()).thenReturn(List.of(limit));
        for (int i = 0; i < 3; i++) {
            assertThat(service.isRateLimited("user-1", "/api/products")).isFalse();
        }
        assertThat(service.isRateLimited("user-1", "/api/products")).isTrue();
    }

    @Test
    @DisplayName("isRateLimited: separate keys (identifier:endpoint) maintain independent counters")
    void isRateLimited_separateKeys() {
        ApiEndpointLimit limit = ApiEndpointLimit.builder()
                .endpoint("/api/products")
                .rateLimitPerMin(2)
                .dailyQuota(1000)
                .build();
        when(apiEndpointLimitRepository.findAll()).thenReturn(List.of(limit));
        service.isRateLimited("user-1", "/api/products");
        service.isRateLimited("user-1", "/api/products");
        assertThat(service.isRateLimited("user-1", "/api/products")).isTrue();
        // user-2 should not be affected
        assertThat(service.isRateLimited("user-2", "/api/products")).isFalse();
    }

    // ---------- getSummaryMetrics ----------

    @Test
    @DisplayName("getSummaryMetrics: aggregates request/success/fail and weighted avg latency")
    void getSummaryMetrics_aggregates() {
        LocalDate today = LocalDate.now();
        ApiMetricDaily m1 = ApiMetricDaily.builder()
                .endpoint("/api/a").method("GET").requestCount(80L).successCount(80L).failCount(0L)
                .avgLatencyMs(100.0).recordedDate(today).recordedHour(0).build();
        ApiMetricDaily m2 = ApiMetricDaily.builder()
                .endpoint("/api/b").method("POST").requestCount(20L).successCount(10L).failCount(10L)
                .avgLatencyMs(200.0).recordedDate(today).recordedHour(0).build();
        when(apiMetricDailyRepository.findByRecordedDate(today)).thenReturn(List.of(m1, m2));
        when(apiEndpointLimitRepository.findAll()).thenReturn(List.of());

        ApiUsageSummaryDTO summary = service.getSummaryMetrics();

        assertThat(summary.getTotalRequestsToday()).isEqualTo(100L);
        assertThat(summary.getSuccessRate()).isEqualTo(90.0); // 90/100
        // avg latency = (100*80 + 200*20) / 100 = (8000 + 4000) / 100 = 120.0
        assertThat(summary.getAvgLatencyMs()).isEqualTo(120.0);
        assertThat(summary.getActiveAlertsCount()).isZero();
    }

    @Test
    @DisplayName("getSummaryMetrics: counts near-quota alerts when usage >= 80% of daily quota")
    void getSummaryMetrics_nearQuotaAlerts() {
        LocalDate today = LocalDate.now();
        ApiMetricDaily m = ApiMetricDaily.builder()
                .endpoint("/api/a").method("GET").requestCount(80L).successCount(80L).failCount(0L)
                .avgLatencyMs(0.0).recordedDate(today).recordedHour(0).build();
        ApiEndpointLimit limit = ApiEndpointLimit.builder()
                .endpoint("/api/a").rateLimitPerMin(100).dailyQuota(100).build();
        when(apiMetricDailyRepository.findByRecordedDate(today)).thenReturn(List.of(m));
        when(apiEndpointLimitRepository.findAll()).thenReturn(List.of(limit));

        ApiUsageSummaryDTO summary = service.getSummaryMetrics();
        assertThat(summary.getActiveAlertsCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("getSummaryMetrics: returns 100% success and 0 avg when no requests")
    void getSummaryMetrics_empty() {
        when(apiMetricDailyRepository.findByRecordedDate(any())).thenReturn(List.of());
        when(apiEndpointLimitRepository.findAll()).thenReturn(List.of());
        ApiUsageSummaryDTO summary = service.getSummaryMetrics();
        assertThat(summary.getTotalRequestsToday()).isZero();
        assertThat(summary.getSuccessRate()).isEqualTo(100.0);
        assertThat(summary.getAvgLatencyMs()).isZero();
        assertThat(summary.getActiveAlertsCount()).isZero();
    }

    // ---------- getEndpointMetrics ----------

    @Test
    @DisplayName("getEndpointMetrics: aggregates per endpoint+method with weighted avg latency")
    void getEndpointMetrics_aggregatesByEndpoint() {
        LocalDate today = LocalDate.now();
        int hour = LocalTime.now().getHour();
        ApiMetricDaily m1 = ApiMetricDaily.builder()
                .endpoint("/api/a").method("GET").requestCount(5L).successCount(5L).failCount(0L)
                .avgLatencyMs(100.0).recordedDate(today).recordedHour(hour).build();
        ApiMetricDaily m2 = ApiMetricDaily.builder()
                .endpoint("/api/a").method("GET").requestCount(5L).successCount(4L).failCount(1L)
                .avgLatencyMs(100.0).recordedDate(today).recordedHour(hour).build();
        ApiEndpointLimit limit = ApiEndpointLimit.builder()
                .endpoint("/api/a").rateLimitPerMin(100).dailyQuota(100).build();
        when(apiMetricDailyRepository.findByRecordedDate(today)).thenReturn(List.of(m1, m2));
        lenient().when(apiEndpointLimitRepository.findAll()).thenReturn(List.of(limit));

        List<ApiEndpointMetricDTO> metrics = service.getEndpointMetrics();
        assertThat(metrics).hasSize(1);
        ApiEndpointMetricDTO dto = metrics.get(0);
        assertThat(dto.getEndpoint()).isEqualTo("/api/a");
        assertThat(dto.getMethod()).isEqualTo("GET");
        assertThat(dto.getRequestCount()).isEqualTo(10L);
        assertThat(dto.getSuccessCount()).isEqualTo(9L);
        assertThat(dto.getFailCount()).isEqualTo(1L);
        assertThat(dto.getAvgLatencyMs()).isEqualTo(100.0);
        assertThat(dto.getRemainingQuota()).isEqualTo(90L);
    }

    @Test
    @DisplayName("getEndpointMetrics: returns empty list when no metrics and no limits")
    void getEndpointMetrics_empty() {
        when(apiMetricDailyRepository.findByRecordedDate(any())).thenReturn(List.of());
        when(apiEndpointLimitRepository.findAll()).thenReturn(List.of());
        List<ApiEndpointMetricDTO> metrics = service.getEndpointMetrics();
        assertThat(metrics).isEmpty();
    }

    @Test
    @DisplayName("getEndpointMetrics: results sorted by requestCount descending")
    void getEndpointMetrics_sortedByRequestCount() {
        LocalDate today = LocalDate.now();
        int hour = LocalTime.now().getHour();
        ApiMetricDaily small = ApiMetricDaily.builder()
                .endpoint("/api/x").method("GET").requestCount(2L).successCount(2L).failCount(0L)
                .avgLatencyMs(0.0).recordedDate(today).recordedHour(hour).build();
        ApiMetricDaily big = ApiMetricDaily.builder()
                .endpoint("/api/y").method("POST").requestCount(10L).successCount(10L).failCount(0L)
                .avgLatencyMs(0.0).recordedDate(today).recordedHour(hour).build();
        when(apiMetricDailyRepository.findByRecordedDate(today)).thenReturn(List.of(small, big));
        when(apiEndpointLimitRepository.findAll()).thenReturn(List.of());

        List<ApiEndpointMetricDTO> metrics = service.getEndpointMetrics();
        assertThat(metrics).hasSize(2);
        assertThat(metrics.get(0).getRequestCount()).isEqualTo(10L);
        assertThat(metrics.get(1).getRequestCount()).isEqualTo(2L);
    }
}