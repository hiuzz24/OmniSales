package fu.osms.inventory.service.impl;

import fu.osms.inventory.dto.response.WarehouseResponse;
import fu.osms.inventory.entity.Warehouse;
import fu.osms.inventory.mapper.WarehouseMapper;
import fu.osms.inventory.repository.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WarehouseServiceImpl - Unit Tests")
class WarehouseServiceImplTest {

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private WarehouseMapper warehouseMapper;

    @InjectMocks
    private WarehouseServiceImpl warehouseService;

    private Warehouse sample;

    @BeforeEach
    void setUp() {
        sample = Warehouse.builder()
                .id(UUID.randomUUID())
                .name("Kho Hà Nội")
                .address("Số 1, Trần Hưng Đạo, HN")
                .isActive(true)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    @Test
    @DisplayName("getAll - returns mapped active warehouses ordered by name")
    void getAll_returnsMappedList() {
        WarehouseResponse dto = WarehouseResponse.builder()
                .id(sample.getId())
                .name(sample.getName())
                .address(sample.getAddress())
                .isActive(true)
                .build();
        when(warehouseRepository.findByIsActiveTrueOrderByNameAsc()).thenReturn(List.of(sample));
        when(warehouseMapper.toResponse(sample)).thenReturn(dto);

        List<WarehouseResponse> result = warehouseService.getAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Kho Hà Nội");
        assertThat(result.get(0).getIsActive()).isTrue();
    }

    @Test
    @DisplayName("getAll - empty list when no active warehouse")
    void getAll_emptyList() {
        when(warehouseRepository.findByIsActiveTrueOrderByNameAsc()).thenReturn(List.of());

        List<WarehouseResponse> result = warehouseService.getAll();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getWarehouseByUserId - delegates to mapper")
    void getWarehouseByUserId_returnsMappedResult() {
        UUID userId = UUID.randomUUID();
        WarehouseResponse dto = WarehouseResponse.builder()
                .id(sample.getId())
                .name(sample.getName())
                .build();
        when(warehouseRepository.findWarehouseByUserId(userId)).thenReturn(sample);
        when(warehouseMapper.toResponse(sample)).thenReturn(dto);

        WarehouseResponse result = warehouseService.getWarehouseByUserId(userId);

        assertThat(result.getId()).isEqualTo(sample.getId());
        assertThat(result.getName()).isEqualTo("Kho Hà Nội");
    }

    @Test
    @DisplayName("getById - throws UnsupportedOperationException (not implemented)")
    void getById_notImplemented() {
        assertThatThrownBy(() -> warehouseService.getById(sample.getId()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("create - throws UnsupportedOperationException (not implemented)")
    void create_notImplemented() {
        assertThatThrownBy(() -> warehouseService.create(
                fu.osms.inventory.dto.request.WarehouseRequest.builder().name("x").build()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("update - throws UnsupportedOperationException (not implemented)")
    void update_notImplemented() {
        assertThatThrownBy(() -> warehouseService.update(sample.getId(),
                fu.osms.inventory.dto.request.WarehouseRequest.builder().name("x").build()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("delete - throws UnsupportedOperationException (not implemented)")
    void delete_notImplemented() {
        assertThatThrownBy(() -> warehouseService.delete(sample.getId()))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
