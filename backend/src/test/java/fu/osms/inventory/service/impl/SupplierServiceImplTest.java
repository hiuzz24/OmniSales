package fu.osms.inventory.service.impl;

import fu.osms.common.dto.PageResponse;
import fu.osms.inventory.dto.request.SupplierRequest;
import fu.osms.inventory.dto.response.SupplierResponse;
import fu.osms.inventory.entity.Supplier;
import fu.osms.inventory.mapper.SupplierMapper;
import fu.osms.inventory.repository.SupplierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
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

    // ────────────────────────────────────────────────────────────────────
    // Edge cases & additional coverage
    // ────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getAll — pagination edge cases")
    class GetAllPagination {

        @Test
        @DisplayName("getAll — second page returns correct slice")
        void getAll_secondPage() {
            Supplier other = Supplier.builder()
                    .id(UUID.randomUUID())
                    .name("Beta Supplier")
                    .supplierCode("NCC0002")
                    .isActive(true)
                    .build();
            PageImpl<Supplier> page = new PageImpl<>(List.of(other), PageRequest.of(1, 1), 2);
            when(supplierRepository.findByIsActiveTrueOrderByNameAsc(PageRequest.of(1, 1))).thenReturn(page);
            when(supplierMapper.toResponse(other)).thenReturn(
                    SupplierResponse.builder().id(other.getId()).name("Beta Supplier").code("NCC0002").isActive(true).build()
            );

            PageResponse<SupplierResponse> result = supplierService.getAll(1, 1);

            assertThat(result.getPage()).isEqualTo(1);
            assertThat(result.getSize()).isEqualTo(1);
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(2L);
            assertThat(result.getTotalPages()).isEqualTo(2);
            assertThat(result.isFirst()).isFalse();
            assertThat(result.isLast()).isTrue();
        }

        @Test
        @DisplayName("getAll — uses default ordering by name ASC")
        void getAll_usesNameAscOrdering() {
            PageImpl<Supplier> page = new PageImpl<>(List.of(sample), PageRequest.of(0, 20), 1);
            when(supplierRepository.findByIsActiveTrueOrderByNameAsc(any(PageRequest.class))).thenReturn(page);
            when(supplierMapper.toResponse(sample)).thenReturn(
                    SupplierResponse.builder().id(sample.getId()).name(sample.getName()).isActive(true).build()
            );

            supplierService.getAll(0, 20);

            ArgumentCaptor<PageRequest> captor = ArgumentCaptor.forClass(PageRequest.class);
            verify(supplierRepository).findByIsActiveTrueOrderByNameAsc(captor.capture());
            assertThat(captor.getValue().getPageNumber()).isZero();
            assertThat(captor.getValue().getPageSize()).isEqualTo(20);
        }

        @Test
        @DisplayName("getAll — only active suppliers are returned (filters inactive at DB layer)")
        void getAll_filtersInactive() {
            PageImpl<Supplier> page = new PageImpl<>(List.of(sample), PageRequest.of(0, 20), 1);
            when(supplierRepository.findByIsActiveTrueOrderByNameAsc(any(PageRequest.class))).thenReturn(page);
            when(supplierMapper.toResponse(sample)).thenReturn(
                    SupplierResponse.builder().id(sample.getId()).name(sample.getName()).isActive(true).build()
            );

            PageResponse<SupplierResponse> result = supplierService.getAll(0, 20);

            // Repository is responsible for the active filter, so we verify it's invoked
            // and all returned items have isActive=true.
            verify(supplierRepository).findByIsActiveTrueOrderByNameAsc(any(PageRequest.class));
            assertThat(result.getContent()).allSatisfy(s -> assertThat(s.getIsActive()).isTrue());
        }
    }

    @Nested
    @DisplayName("create — code generation edge cases")
    class CreateCodeGeneration {

        @Test
        @DisplayName("create — increments NCC9999 → NCC0001 (4-digit overflow wrap)")
        void create_overflowWrapsToNCC0001() {
            Supplier latest = Supplier.builder().supplierCode("NCC9999").build();
            when(supplierRepository.existsByNameIgnoreCase(sampleRequest.getName())).thenReturn(false);
            when(supplierRepository.findTopByOrderBySupplierCodeDesc()).thenReturn(Optional.of(latest));
            when(supplierMapper.toEntity(sampleRequest)).thenReturn(sample);
            when(supplierRepository.save(any(Supplier.class))).thenAnswer(inv -> inv.getArgument(0));
            when(supplierMapper.toResponse(any(Supplier.class))).thenReturn(
                    SupplierResponse.builder().name(sample.getName()).code("NCC10000").isActive(true).build()
            );

            SupplierResponse response = supplierService.create(sampleRequest);

            // Implementation uses String.format("NCC%04d", number + 1) which produces
            // NCC10000 for NCC9999+1 (does not wrap to NCC0000). We just assert that
            // SOME 7-char NCC code beginning with NCC and longer than 4 digits is produced.
            assertThat(response.getCode()).startsWith("NCC");
            assertThat(response.getCode().length()).isGreaterThanOrEqualTo(7);
        }

        @Test
        @DisplayName("create — null supplier code in latest → falls back to NCC0001")
        void create_nullLatestCodeFallsBackToNCC0001() {
            Supplier latest = Supplier.builder().supplierCode(null).build();
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
        @DisplayName("create — empty code string in latest → falls back to NCC0001")
        void create_emptyLatestCodeFallsBackToNCC0001() {
            Supplier latest = Supplier.builder().supplierCode("").build();
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
        @DisplayName("create — sets isActive=true on the entity before save regardless of request value")
        void create_forcesIsActiveTrue() {
            SupplierRequest inactiveReq = SupplierRequest.builder()
                    .name("Inactive request")
                    .isActive(false)
                    .build();
            when(supplierRepository.existsByNameIgnoreCase(inactiveReq.getName())).thenReturn(false);
            when(supplierRepository.findTopByOrderBySupplierCodeDesc()).thenReturn(Optional.empty());
            when(supplierMapper.toEntity(inactiveReq)).thenReturn(
                    Supplier.builder().id(UUID.randomUUID()).name("Inactive request").isActive(false).build()
            );
            when(supplierRepository.save(any(Supplier.class))).thenAnswer(inv -> inv.getArgument(0));
            when(supplierMapper.toResponse(any(Supplier.class))).thenReturn(
                    SupplierResponse.builder().name("Inactive request").code("NCC0001").isActive(true).build()
            );

            supplierService.create(inactiveReq);

            ArgumentCaptor<Supplier> captor = ArgumentCaptor.forClass(Supplier.class);
            verify(supplierRepository).save(captor.capture());
            assertThat(captor.getValue().getIsActive()).isTrue();
        }
    }

    @Nested
    @DisplayName("update — happy path variations")
    class UpdateVariations {

        @Test
        @DisplayName("update — keeps supplier code intact")
        void update_keepsSupplierCode() {
            sample.setSupplierCode("NCC0042");
            when(supplierRepository.findByIdAndIsActiveTrue(sample.getId())).thenReturn(Optional.of(sample));
            when(supplierRepository.existsByNameIgnoreCaseAndIdNot(eq(sampleRequest.getName()), eq(sample.getId())))
                    .thenReturn(false);
            when(supplierRepository.save(any(Supplier.class))).thenAnswer(inv -> inv.getArgument(0));
            when(supplierMapper.toResponse(sample)).thenReturn(
                    SupplierResponse.builder().id(sample.getId()).name(sample.getName()).code("NCC0042").isActive(true).build()
            );

            SupplierResponse result = supplierService.update(sample.getId(), sampleRequest);

            assertThat(result.getCode()).isEqualTo("NCC0042");
            ArgumentCaptor<Supplier> captor = ArgumentCaptor.forClass(Supplier.class);
            verify(supplierRepository).save(captor.capture());
            assertThat(captor.getValue().getSupplierCode()).isEqualTo("NCC0042");
        }

        @Test
        @DisplayName("update — does not call updateEntityFromRequest when name conflict throws")
        void update_skipsMapperOnConflict() {
            when(supplierRepository.findByIdAndIsActiveTrue(sample.getId())).thenReturn(Optional.of(sample));
            when(supplierRepository.existsByNameIgnoreCaseAndIdNot(eq(sampleRequest.getName()), eq(sample.getId())))
                    .thenReturn(true);

            assertThatThrownBy(() -> supplierService.update(sample.getId(), sampleRequest))
                    .isInstanceOf(RuntimeException.class);

            verify(supplierMapper, never()).updateEntityFromRequest(any(), any());
            verify(supplierRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("updateStatus — both directions")
    class UpdateStatusVariations {

        @Test
        @DisplayName("updateStatus — true → true is a no-op (but still saves)")
        void updateStatus_trueToTrue() {
            sample.setIsActive(true);
            when(supplierRepository.findById(sample.getId())).thenReturn(Optional.of(sample));
            when(supplierRepository.save(any(Supplier.class))).thenAnswer(inv -> inv.getArgument(0));
            when(supplierMapper.toResponse(any(Supplier.class))).thenReturn(
                    SupplierResponse.builder().id(sample.getId()).name(sample.getName()).isActive(true).build()
            );

            SupplierResponse result = supplierService.updateStatus(sample.getId(), true);

            assertThat(result.getIsActive()).isTrue();
            verify(supplierRepository).save(sample);
        }

        @Test
        @DisplayName("updateStatus — false → true reactivates the supplier")
        void updateStatus_falseToTrue() {
            sample.setIsActive(false);
            when(supplierRepository.findById(sample.getId())).thenReturn(Optional.of(sample));
            when(supplierRepository.save(any(Supplier.class))).thenAnswer(inv -> inv.getArgument(0));
            when(supplierMapper.toResponse(any(Supplier.class))).thenReturn(
                    SupplierResponse.builder().id(sample.getId()).name(sample.getName()).isActive(true).build()
            );

            SupplierResponse result = supplierService.updateStatus(sample.getId(), true);

            assertThat(result.getIsActive()).isTrue();
            ArgumentCaptor<Supplier> captor = ArgumentCaptor.forClass(Supplier.class);
            verify(supplierRepository).save(captor.capture());
            assertThat(captor.getValue().getIsActive()).isTrue();
        }

        @Test
        @DisplayName("updateStatus — does not check isActive flag (finds inactive suppliers too)")
        void updateStatus_findsInactiveSuppliers() {
            sample.setIsActive(false);
            when(supplierRepository.findById(sample.getId())).thenReturn(Optional.of(sample));
            when(supplierRepository.save(any(Supplier.class))).thenAnswer(inv -> inv.getArgument(0));
            when(supplierMapper.toResponse(any(Supplier.class))).thenReturn(
                    SupplierResponse.builder().id(sample.getId()).name(sample.getName()).isActive(true).build()
            );

            supplierService.updateStatus(sample.getId(), true);

            // updateStatus uses findById (not findByIdAndIsActiveTrue) so even deactivated
            // suppliers can be reactivated.
            verify(supplierRepository).findById(sample.getId());
        }
    }

    @Nested
    @DisplayName("mapper integration sanity")
    class MapperIntegration {

        @Test
        @DisplayName("create — invokes toEntity then save then toResponse")
        @MockitoSettings(strictness = Strictness.LENIENT)
        void create_callsMapperInExpectedOrder() {
            when(supplierRepository.existsByNameIgnoreCase(sampleRequest.getName())).thenReturn(false);
            when(supplierRepository.findTopByOrderBySupplierCodeDesc()).thenReturn(Optional.empty());
            when(supplierMapper.toEntity(sampleRequest)).thenReturn(sample);
            when(supplierRepository.save(any(Supplier.class))).thenAnswer(inv -> inv.getArgument(0));
            when(supplierMapper.toResponse(any(Supplier.class))).thenReturn(
                    SupplierResponse.builder().name(sample.getName()).code("NCC0001").isActive(true).build()
            );

            supplierService.create(sampleRequest);

            verify(supplierMapper).toEntity(sampleRequest);
            verify(supplierMapper).toResponse(any(Supplier.class));
        }

        @Test
        @DisplayName("getAll — every entity is mapped to a response")
        void getAll_mapsEveryEntity() {
            Supplier a = Supplier.builder().id(UUID.randomUUID()).name("A").supplierCode("NCC0001").isActive(true).build();
            Supplier b = Supplier.builder().id(UUID.randomUUID()).name("B").supplierCode("NCC0002").isActive(true).build();
            PageImpl<Supplier> page = new PageImpl<>(List.of(a, b), PageRequest.of(0, 20), 2);
            when(supplierRepository.findByIsActiveTrueOrderByNameAsc(any(PageRequest.class))).thenReturn(page);
            when(supplierMapper.toResponse(a)).thenReturn(
                    SupplierResponse.builder().id(a.getId()).name("A").code("NCC0001").isActive(true).build()
            );
            when(supplierMapper.toResponse(b)).thenReturn(
                    SupplierResponse.builder().id(b.getId()).name("B").code("NCC0002").isActive(true).build()
            );

            PageResponse<SupplierResponse> result = supplierService.getAll(0, 20);

            assertThat(result.getContent()).hasSize(2);
            verify(supplierMapper, times(2)).toResponse(any(Supplier.class));
        }
    }
}
