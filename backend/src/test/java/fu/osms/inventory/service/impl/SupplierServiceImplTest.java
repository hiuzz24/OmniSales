package fu.osms.inventory.service.impl;

import fu.osms.common.dto.PageResponse;
import fu.osms.inventory.dto.request.SupplierRequest;
import fu.osms.inventory.dto.response.SupplierResponse;
import fu.osms.inventory.entity.Supplier;
import fu.osms.inventory.mapper.SupplierMapper;
import fu.osms.inventory.repository.SupplierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SupplierServiceImpl - Unit Tests")
class SupplierServiceImplTest {

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private SupplierMapper supplierMapper;

    @InjectMocks
    private SupplierServiceImpl supplierService;

    private Supplier sample;
    private SupplierRequest sampleRequest;

    @BeforeEach
    void setUp() {
        sample = Supplier.builder()
                .id(UUID.randomUUID())
                .name("Công ty TNHH ABC")
                .contactName("Nguyen Van A")
                .phone("0901234567")
                .email("abc@example.com")
                .address("Hà Nội")
                .supplierCode("NCC0001")
                .isActive(true)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        sampleRequest = SupplierRequest.builder()
                .name("Công ty TNHH ABC")
                .contactName("Nguyen Van A")
                .phone("0901234567")
                .email("abc@example.com")
                .address("Hà Nội")
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("getAll - returns paginated active suppliers mapped to response")
    void getAll_returnsPagedSupplierResponse() {
        PageImpl<Supplier> page = new PageImpl<>(List.of(sample), PageRequest.of(0, 20), 1);
        SupplierResponse mapped = SupplierResponse.builder()
                .id(sample.getId())
                .name(sample.getName())
                .code(sample.getSupplierCode())
                .isActive(true)
                .build();
        when(supplierRepository.findByIsActiveTrueOrderByNameAsc(any(PageRequest.class))).thenReturn(page);
        when(supplierMapper.toResponse(sample)).thenReturn(mapped);

        PageResponse<SupplierResponse> result = supplierService.getAll(0, 20);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("Công ty TNHH ABC");
        assertThat(result.getTotalElements()).isEqualTo(1L);
        assertThat(result.getTotalPages()).isEqualTo(1);
        assertThat(result.isFirst()).isTrue();
        assertThat(result.isLast()).isTrue();
    }

    @Test
    @DisplayName("getAll - empty result returns empty content")
    void getAll_emptyResultReturnsEmptyPage() {
        PageImpl<Supplier> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(supplierRepository.findByIsActiveTrueOrderByNameAsc(any(PageRequest.class))).thenReturn(page);

        PageResponse<SupplierResponse> result = supplierService.getAll(0, 20);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("create - generates NCC0001 when no prior supplier exists")
    void create_generatesFirstCode() {
        when(supplierRepository.existsByNameIgnoreCase(sampleRequest.getName())).thenReturn(false);
        when(supplierRepository.findTopByOrderBySupplierCodeDesc()).thenReturn(Optional.empty());
        when(supplierMapper.toEntity(sampleRequest)).thenReturn(sample);
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(inv -> inv.getArgument(0));
        when(supplierMapper.toResponse(any(Supplier.class))).thenReturn(
                SupplierResponse.builder().name(sample.getName()).code("NCC0001").isActive(true).build()
        );

        SupplierResponse response = supplierService.create(sampleRequest);

                assertThat(response.getCode()).isEqualTo("NCC0001");
        ArgumentCaptor<Supplier> captor = ArgumentCaptor.forClass(Supplier.class);
        verify(supplierRepository).save(captor.capture());
        assertThat(captor.getValue().getIsActive()).isTrue();
    }

    @Test
    @DisplayName("create - increments supplier code when prior code exists")
    void create_incrementsSupplierCode() {
        Supplier latest = Supplier.builder().supplierCode("NCC0042").build();
        when(supplierRepository.existsByNameIgnoreCase(sampleRequest.getName())).thenReturn(false);
        when(supplierRepository.findTopByOrderBySupplierCodeDesc()).thenReturn(Optional.of(latest));
        when(supplierMapper.toEntity(sampleRequest)).thenReturn(sample);
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(inv -> inv.getArgument(0));
        when(supplierMapper.toResponse(any(Supplier.class))).thenReturn(
                SupplierResponse.builder().name(sample.getName()).code("NCC0043").isActive(true).build()
        );

        SupplierResponse response = supplierService.create(sampleRequest);

        assertThat(response.getCode()).isEqualTo("NCC0043");
    }

    @Test
    @DisplayName("create - throws when name already exists (case-insensitive)")
    void create_duplicateNameThrows() {
        when(supplierRepository.existsByNameIgnoreCase(sampleRequest.getName())).thenReturn(true);

        assertThatThrownBy(() -> supplierService.create(sampleRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("already exists");

        verify(supplierRepository, never()).save(any());
    }

    @Test
    @DisplayName("create - falls back to NCC0001 if existing code is corrupted")
    void create_corruptedCodeFallsBackToNCC0001() {
        Supplier latest = Supplier.builder().supplierCode("BROKEN_CODE").build();
        when(supplierRepository.existsByNameIgnoreCase(sampleRequest.getName())).thenReturn(false);
        when(supplierRepository.findTopByOrderBySupplierCodeDesc()).thenReturn(Optional.of(latest));
        when(supplierMapper.toEntity(sampleRequest)).thenReturn(sample);
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(inv -> inv.getArgument(0));
        when(supplierMapper.toResponse(any(Supplier.class))).thenReturn(
                SupplierResponse.builder().name(sample.getName()).code("NCC0001").isActive(true).build()
        );

        SupplierResponse response = supplierService.create(sampleRequest);

        assertThat(response.getCode()).isEqualTo("NCC0001");
    }

    @Test
    @DisplayName("update - throws when supplier not found")
    void update_supplierNotFoundThrows() {
        when(supplierRepository.findByIdAndIsActiveTrue(sample.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> supplierService.update(sample.getId(), sampleRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not found");

        verify(supplierRepository, never()).save(any());
    }

    @Test
    @DisplayName("update - throws when name conflicts with another supplier")
    void update_nameConflictThrows() {
        when(supplierRepository.findByIdAndIsActiveTrue(sample.getId())).thenReturn(Optional.of(sample));
        when(supplierRepository.existsByNameIgnoreCaseAndIdNot(eq(sampleRequest.getName()), eq(sample.getId())))
                .thenReturn(true);

        assertThatThrownBy(() -> supplierService.update(sample.getId(), sampleRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("update - updates fields and saves")
    void update_succeeds() {
        when(supplierRepository.findByIdAndIsActiveTrue(sample.getId())).thenReturn(Optional.of(sample));
        when(supplierRepository.existsByNameIgnoreCaseAndIdNot(eq(sampleRequest.getName()), eq(sample.getId())))
                .thenReturn(false);
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(inv -> inv.getArgument(0));
        when(supplierMapper.toResponse(sample)).thenReturn(
                SupplierResponse.builder().id(sample.getId()).name(sample.getName()).isActive(true).build()
        );

        SupplierResponse result = supplierService.update(sample.getId(), sampleRequest);

        assertThat(result.getName()).isEqualTo(sample.getName());
        verify(supplierMapper, times(1)).updateEntityFromRequest(sampleRequest, sample);
        verify(supplierRepository, times(1)).save(sample);
    }

    @Test
    @DisplayName("updateStatus - throws when supplier not found")
    void updateStatus_notFoundThrows() {
        when(supplierRepository.findById(sample.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> supplierService.updateStatus(sample.getId(), false))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("updateStatus - flips isActive and saves")
    void updateStatus_togglesActive() {
        sample.setIsActive(true);
        when(supplierRepository.findById(sample.getId())).thenReturn(Optional.of(sample));
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(inv -> inv.getArgument(0));
        when(supplierMapper.toResponse(any(Supplier.class))).thenReturn(
                SupplierResponse.builder().id(sample.getId()).name(sample.getName()).isActive(false).build()
        );

        SupplierResponse result = supplierService.updateStatus(sample.getId(), false);

        assertThat(result.getIsActive()).isFalse();
        ArgumentCaptor<Supplier> captor = ArgumentCaptor.forClass(Supplier.class);
        verify(supplierRepository).save(captor.capture());
        assertThat(captor.getValue().getIsActive()).isFalse();
    }
}
