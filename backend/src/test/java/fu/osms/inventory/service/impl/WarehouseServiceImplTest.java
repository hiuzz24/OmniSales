package fu.osms.inventory.service.impl;

import fu.osms.auth.repository.UserRepository;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.inventory.dto.request.WarehouseRequest;
import fu.osms.inventory.dto.response.WarehouseResponse;
import fu.osms.inventory.entity.Warehouse;
import fu.osms.inventory.mapper.WarehouseMapper;
import fu.osms.inventory.repository.InventoryItemRepository;
import fu.osms.inventory.repository.WarehouseRepository;
import fu.osms.sync.service.MarketplaceWarehouseConsistencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("WarehouseServiceImpl - Unit Tests")
class WarehouseServiceImplTest {

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private WarehouseMapper warehouseMapper;

    @Mock
    private UserRepository userRepository;

    @Mock
    private InventoryItemRepository inventoryItemRepository;

    @Mock
    private MarketplaceWarehouseConsistencyService marketplaceWarehouseConsistencyService;

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

    private WarehouseResponse stubMapping(Warehouse warehouse) {
        WarehouseResponse dto = WarehouseResponse.builder()
                .id(warehouse.getId())
                .name(warehouse.getName())
                .address(warehouse.getAddress())
                .isActive(warehouse.getIsActive())
                .staffCount(0)
                .productCount(0)
                .totalStock(0)
                .build();
        lenient().when(warehouseMapper.toResponse(warehouse)).thenReturn(dto);
        return dto;
    }

    @Nested
    @DisplayName("getAll() Tests")
    class GetAllTests {

        @Test
        @DisplayName("getAll - returns mapped active warehouses ordered by name")
        void getAll_returnsMappedList() {
            WarehouseResponse dto = stubMapping(sample);
            when(warehouseRepository.searchWarehouses(any(), any())).thenReturn(List.of(sample));

            List<WarehouseResponse> result = warehouseService.getAll();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("Kho Hà Nội");
            assertThat(result.get(0).getIsActive()).isTrue();
        }

        @Test
        @DisplayName("getAll - empty list when no active warehouse")
        void getAll_emptyList() {
            when(warehouseRepository.searchWarehouses(any(), any())).thenReturn(Collections.emptyList());

            List<WarehouseResponse> result = warehouseService.getAll();

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("getAll - filters by ACTIVE status")
        void getAll_activeFilter() {
            WarehouseResponse dto = stubMapping(sample);
            when(warehouseRepository.searchWarehouses(any(), eq(true))).thenReturn(List.of(sample));

            List<WarehouseResponse> result = warehouseService.getAll(null, "ACTIVE");

            assertThat(result).hasSize(1);
            verify(warehouseRepository).searchWarehouses(null, true);
        }

        @Test
        @DisplayName("getAll - filters by INACTIVE status")
        void getAll_inactiveFilter() {
            when(warehouseRepository.searchWarehouses(any(), eq(false))).thenReturn(Collections.emptyList());

            List<WarehouseResponse> result = warehouseService.getAll(null, "INACTIVE");

            assertThat(result).isEmpty();
            verify(warehouseRepository).searchWarehouses(null, false);
        }

        @Test
        @DisplayName("getAll - keyword is trimmed and forwarded")
        void getAll_keywordTrimmed() {
            when(warehouseRepository.searchWarehouses(eq("ha noi"), any())).thenReturn(Collections.emptyList());

            warehouseService.getAll("  Ha Noi  ", null);

            verify(warehouseRepository).searchWarehouses("Ha Noi", null);
        }
    }

    @Nested
    @DisplayName("getById() Tests")
    class GetByIdTests {

        @Test
        @DisplayName("getById - returns mapped warehouse with stats")
        void getById_returnsMappedWarehouse() {
            WarehouseResponse dto = stubMapping(sample);
            when(warehouseRepository.findById(sample.getId())).thenReturn(java.util.Optional.of(sample));

            WarehouseResponse result = warehouseService.getById(sample.getId());

            assertThat(result.getId()).isEqualTo(sample.getId());
            assertThat(result.getName()).isEqualTo("Kho Hà Nội");
        }

        @Test
        @DisplayName("getById - throws AppException(RESOURCE_NOT_FOUND) when warehouse missing")
        void getById_notFound() {
            UUID id = UUID.randomUUID();
            when(warehouseRepository.findById(id)).thenReturn(java.util.Optional.empty());

            assertThatThrownBy(() -> warehouseService.getById(id))
                    .isInstanceOf(AppException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("create() Tests")
    class CreateTests {

        @Test
        @DisplayName("create - trims name and address, persists new warehouse")
        void create_persists() {
            WarehouseRequest req = WarehouseRequest.builder()
                    .name("  Kho Mới  ")
                    .address("  123 Đường ABC  ")
                    .isActive(true)
                    .build();
            Warehouse saved = Warehouse.builder()
                    .id(UUID.randomUUID())
                    .name("Kho Mới")
                    .address("123 Đường ABC")
                    .isActive(true)
                    .build();
            WarehouseResponse dto = stubMapping(saved);

            when(warehouseRepository.save(any(Warehouse.class))).thenReturn(saved);

            WarehouseResponse result = warehouseService.create(req);

            assertThat(result).isSameAs(dto);
            verify(warehouseRepository).save(any(Warehouse.class));
        }

        @Test
        @DisplayName("create - default isActive is true when not provided")
        void create_defaultActive() {
            WarehouseRequest req = WarehouseRequest.builder()
                    .name("Kho Default")
                    .address(null)
                    .isActive(null)
                    .build();
            when(warehouseRepository.save(any(Warehouse.class))).thenAnswer(invocation -> {
                Warehouse persisted = invocation.getArgument(0);
                assertThat(persisted.getIsActive()).isTrue();
                persisted.setId(UUID.randomUUID());
                return persisted;
            });
            stubMapping(Warehouse.builder().id(UUID.randomUUID()).name("Kho Default").build());

            warehouseService.create(req);

            verify(warehouseRepository).save(any(Warehouse.class));
        }
    }

    @Nested
    @DisplayName("update() Tests")
    class UpdateTests {

        @Test
        @DisplayName("update - mutates name/address/isActive and saves")
        void update_persistsChanges() {
            WarehouseRequest req = WarehouseRequest.builder()
                    .name("  Kho Updated  ")
                    .address("  456 XYZ  ")
                    .isActive(false)
                    .build();
            when(warehouseRepository.findById(sample.getId())).thenReturn(java.util.Optional.of(sample));
            when(warehouseRepository.save(any(Warehouse.class))).thenReturn(sample);
            stubMapping(sample);

            warehouseService.update(sample.getId(), req);

            assertThat(sample.getName()).isEqualTo("Kho Updated");
            assertThat(sample.getAddress()).isEqualTo("456 XYZ");
            assertThat(sample.getIsActive()).isFalse();
            verify(warehouseRepository).save(sample);
        }

        @Test
        @DisplayName("update - does not change isActive when null in request")
        void update_keepsActiveWhenNull() {
            sample.setIsActive(true);
            WarehouseRequest req = WarehouseRequest.builder()
                    .name("Kho")
                    .address("Địa chỉ")
                    .isActive(null)
                    .build();
            when(warehouseRepository.findById(sample.getId())).thenReturn(java.util.Optional.of(sample));
            when(warehouseRepository.save(any(Warehouse.class))).thenReturn(sample);
            stubMapping(sample);

            warehouseService.update(sample.getId(), req);

            assertThat(sample.getIsActive()).isTrue();
        }

        @Test
        @DisplayName("update - throws AppException(RESOURCE_NOT_FOUND) when missing")
        void update_notFound() {
            UUID id = UUID.randomUUID();
            when(warehouseRepository.findById(id)).thenReturn(java.util.Optional.empty());

            assertThatThrownBy(() -> warehouseService.update(id, WarehouseRequest.builder().name("X").build()))
                    .isInstanceOf(AppException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("toggleStatus() Tests")
    class ToggleStatusTests {

        @Test
        @DisplayName("toggleStatus - toggles isActive and saves")
        void toggleStatus_persists() {
            when(warehouseRepository.findById(sample.getId())).thenReturn(java.util.Optional.of(sample));
            when(warehouseRepository.save(any(Warehouse.class))).thenReturn(sample);
            stubMapping(sample);

            warehouseService.toggleStatus(sample.getId(), false);

            assertThat(sample.getIsActive()).isFalse();
            verify(warehouseRepository).save(sample);
        }
    }

    @Nested
    @DisplayName("delete() Tests")
    class DeleteTests {

        @Test
        @DisplayName("delete - soft-deletes warehouse by setting deletedAt")
        void delete_softDeletes() {
            when(warehouseRepository.findById(sample.getId())).thenReturn(java.util.Optional.of(sample));
            when(warehouseRepository.save(any(Warehouse.class))).thenReturn(sample);

            warehouseService.delete(sample.getId());

            assertThat(sample.getDeletedAt()).isNotNull();
            verify(warehouseRepository).save(sample);
        }

        @Test
        @DisplayName("delete - throws AppException(RESOURCE_NOT_FOUND) when missing")
        void delete_notFound() {
            UUID id = UUID.randomUUID();
            when(warehouseRepository.findById(id)).thenReturn(java.util.Optional.empty());

            assertThatThrownBy(() -> warehouseService.delete(id))
                    .isInstanceOf(AppException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("getWarehouseByUserId() Tests")
    class GetByUserTests {

        @Test
        @DisplayName("getWarehouseByUserId - delegates to repository and mapper")
        void getWarehouseByUserId_returnsMappedResult() {
            UUID userId = UUID.randomUUID();
            WarehouseResponse dto = WarehouseResponse.builder()
                    .id(sample.getId())
                    .name(sample.getName())
                    .build();
            when(warehouseRepository.findWarehouseByUserId(userId)).thenReturn(sample);
            when(warehouseMapper.toResponse(sample)).thenReturn(dto);
            when(userRepository.countByWarehouseId(sample.getId())).thenReturn(0);
            when(inventoryItemRepository.countProductTypesByWarehouseId(sample.getId())).thenReturn(0);
            when(inventoryItemRepository.sumTotalStockByWarehouseId(sample.getId())).thenReturn(0);

            WarehouseResponse result = warehouseService.getWarehouseByUserId(userId);

            assertThat(result.getId()).isEqualTo(sample.getId());
            assertThat(result.getName()).isEqualTo("Kho Hà Nội");
        }
    }

    @Nested
    @DisplayName("getMaster() Tests")
    class GetMasterTests {

        @Test
        @DisplayName("getMaster - resolves the master warehouse through consistency service")
        void getMaster_returnsMaster() {
            Warehouse master = Warehouse.builder()
                    .id(UUID.randomUUID())
                    .name("Master Warehouse")
                    .isActive(true)
                    .build();
            when(marketplaceWarehouseConsistencyService.resolveMasterWarehouse()).thenReturn(master);
            stubMapping(master);

            WarehouseResponse result = warehouseService.getMaster();

            assertThat(result.getName()).isEqualTo("Master Warehouse");
            verify(marketplaceWarehouseConsistencyService).resolveMasterWarehouse();
        }
    }
}