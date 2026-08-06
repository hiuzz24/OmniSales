package fu.osms.catalog.service.impl;

import fu.osms.catalog.dto.response.ProductLogResponse;
import fu.osms.catalog.entity.ProductLog;
import fu.osms.catalog.mapper.ProductLogMapper;
import fu.osms.catalog.repository.ProductLogRepository;
import fu.osms.common.dto.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductLogServiceImpl Tests")
class ProductLogServiceImplTest {

    @Mock private ProductLogRepository productLogRepository;
    @Mock private ProductLogMapper productLogMapper;

    private ProductLogServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ProductLogServiceImpl(productLogRepository, productLogMapper);
    }

    private ProductLog log(UUID id, UUID productId) {
        return ProductLog.builder()
                .id(id)
                .product(new fu.osms.catalog.entity.Product())
                .sku("TEST-" + id)
                .fieldChanges(java.util.Map.of())
                .build();
    }

    private ProductLogResponse response(UUID id) {
        return ProductLogResponse.builder().id(id).sku("TEST-" + id).build();
    }

    @Test
    @DisplayName("getLogs: filters by productId when provided")
    void getLogs_byProductId() {
        UUID productId = UUID.randomUUID();
        UUID logId = UUID.randomUUID();
        ProductLog log = log(logId, productId);
        Page<ProductLog> page = new PageImpl<>(List.of(log), PageRequest.of(0, 10), 1);
        when(productLogRepository.findByProductIdOrderByPerformedAtDesc(eq(productId), any(Pageable.class)))
                .thenReturn(page);
        when(productLogMapper.toResponse(log)).thenReturn(response(logId));

        PageResponse<ProductLogResponse> result = service.getLogs(productId, 0, 10, null);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(logId);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("getLogs: returns all logs when productId is null")
    void getLogs_allLogs() {
        UUID logId = UUID.randomUUID();
        ProductLog log = log(logId, null);
        Page<ProductLog> page = new PageImpl<>(List.of(log), PageRequest.of(0, 10), 1);
        when(productLogRepository.findAll(any(Pageable.class))).thenReturn(page);
        when(productLogMapper.toResponse(log)).thenReturn(response(logId));

        PageResponse<ProductLogResponse> result = service.getLogs(null, 0, 10, null);

        assertThat(result.getContent()).hasSize(1);
        verify(productLogRepository).findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("getLogs: defaults to 'performedAt,desc' when sortParam is null")
    void getLogs_defaultSort() {
        when(productLogRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.getLogs(null, 0, 10, null);

        org.mockito.ArgumentCaptor<Pageable> captor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(productLogRepository).findAll(captor.capture());
        Sort.Order order = captor.getValue().getSort().getOrderFor("performedAt");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @DisplayName("getLogs: parses 'field,asc' sortParam")
    void getLogs_ascSort() {
        when(productLogRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.getLogs(null, 0, 10, "sku,asc");

        org.mockito.ArgumentCaptor<Pageable> captor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(productLogRepository).findAll(captor.capture());
        Sort.Order order = captor.getValue().getSort().getOrderFor("sku");
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    @DisplayName("getLogs: parses 'field,desc' sortParam")
    void getLogs_descSort() {
        when(productLogRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.getLogs(null, 0, 10, "sku,desc");

        org.mockito.ArgumentCaptor<Pageable> captor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(productLogRepository).findAll(captor.capture());
        Sort.Order order = captor.getValue().getSort().getOrderFor("sku");
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @DisplayName("getLogs: returns empty page when no logs exist")
    void getLogs_empty() {
        when(productLogRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        PageResponse<ProductLogResponse> result = service.getLogs(null, 0, 10, null);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }
}
