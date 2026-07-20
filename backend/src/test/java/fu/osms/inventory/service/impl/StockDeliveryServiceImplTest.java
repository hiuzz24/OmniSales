package fu.osms.inventory.service.impl;

import fu.osms.auth.entity.User;
import fu.osms.auth.repository.UserRepository;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.catalog.repository.ProductVariantRepository;
import fu.osms.common.exception.AppException;
import fu.osms.inventory.dto.request.StockDeliveryItemRequest;
import fu.osms.inventory.dto.request.StockDeliveryRequest;
import fu.osms.inventory.dto.response.StockDeliveryResponse;
import fu.osms.inventory.entity.*;
import fu.osms.inventory.mapper.StockDeliveryMapper;
import fu.osms.inventory.repository.*;
import fu.osms.inventory.service.InventoryAlertService;
import fu.osms.sync.service.MarketplaceInventoryPropagationService;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("StockDeliveryServiceImpl Tests")
class StockDeliveryServiceImplTest {

    @Mock
    private InventoryIssueRepository inventoryIssueRepository;
    @Mock
    private InventoryIssueItemRepository inventoryIssueItemRepository;
    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private ProductVariantRepository productVariantRepository;
    @Mock
    private InventoryItemRepository inventoryItemRepository;
    @Mock
    private InventoryTransactionRepository inventoryTransactionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private StockDeliveryMapper stockDeliveryMapper;
    @Mock
    private InventoryAlertService inventoryAlertService;
    @Mock
    private MarketplaceInventoryPropagationService marketplaceInventoryPropagationService;
    @Mock
    private MarketplaceWarehouseConsistencyService marketplaceWarehouseConsistencyService;

    @InjectMocks
    private StockDeliveryServiceImpl stockDeliveryService;

    // Test data
    private UUID warehouseId;
    private UUID variantId;
    private UUID userId;
    private UUID deliveryId;
    private UUID inventoryItemId;

    private Warehouse warehouse;
    private Warehouse inactiveWarehouse;
    private ProductVariant variant;
    private User user;
    private InventoryIssue delivery;
    private StockDeliveryResponse response;

    @BeforeEach
    void setUp() {
        warehouseId = UUID.randomUUID();
        variantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        deliveryId = UUID.randomUUID();
        inventoryItemId = UUID.randomUUID();

        warehouse = Warehouse.builder()
                .id(warehouseId)
                .name("Test Warehouse")
                .isActive(true)
                .build();

        inactiveWarehouse = Warehouse.builder()
                .id(warehouseId)
                .name("Inactive Warehouse")
                .isActive(false)
                .build();

        variant = ProductVariant.builder()
                .id(variantId)
                .sku("TEST-VAR-001")
                .name("Test Variant")
                .isActive(true)
                .build();

        user = User.builder()
                .id(userId)
                .email("test@osms.vn")
                .fullName("Test User")
                .build();

        delivery = InventoryIssue.builder()
                .id(deliveryId)
                .warehouse(warehouse)
                .issueCode("XK-2024-001")
                .issueType("ORDER")
                .status("DRAFT")
                .totalCost(BigDecimal.ZERO)
                .createdBy(user)
                .items(new ArrayList<>())
                .build();

        response = StockDeliveryResponse.builder()
                .id(deliveryId)
                .issueCode("XK-2024-001")
                .issueType("ORDER")
                .status("DRAFT")
                .totalCost(BigDecimal.ZERO)
                .build();
    }

    // =========================================================
    // VALIDATION TESTS
    // =========================================================

    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("Should throw when warehouse is inactive")
        void shouldThrowWhenWarehouseInactive() {
            StockDeliveryRequest request = new StockDeliveryRequest();
            request.setWarehouseId(warehouseId);
            request.setDeliveryType("ORDER");
            request.setIssuedDate(LocalDate.now());
            request.setItems(List.of());

            when(marketplaceWarehouseConsistencyService.resolveMasterWarehouse())
                    .thenReturn(inactiveWarehouse);

            assertThatThrownBy(() -> stockDeliveryService.createStockDelivery(request))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("delivery warehouse");
        }

        @Test
        @DisplayName("Should throw when warehouse not found")
        void shouldThrowWhenWarehouseNotFound() {
            StockDeliveryRequest request = new StockDeliveryRequest();
            request.setWarehouseId(warehouseId);
            request.setDeliveryType("ORDER");
            request.setIssuedDate(LocalDate.now());
            request.setItems(List.of());

            when(marketplaceWarehouseConsistencyService.resolveMasterWarehouse())
                    .thenThrow(new AppException(fu.osms.common.exception.ErrorCode.WAREHOUSE_NOT_FOUND));

            assertThatThrownBy(() -> stockDeliveryService.createStockDelivery(request))
                    .isInstanceOf(AppException.class);
        }
    }

    // =========================================================
    // GET DELIVERIES TESTS
    // =========================================================

    @Nested
    @DisplayName("getAllStockDeliveries() Tests")
    class GetDeliveriesTests {

        @Test
        @DisplayName("Should return paginated list of deliveries")
        void shouldReturnPaginatedDeliveries() {
            Page<InventoryIssue> page = new PageImpl<>(List.of(delivery));
            when(inventoryIssueRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
            when(stockDeliveryMapper.toResponse(any(InventoryIssue.class))).thenReturn(response);

            Page<StockDeliveryResponse> result = stockDeliveryService.getAllStockDeliveries(
                    null, null, null, null, null, null, PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("Should filter by delivery type")
        void shouldFilterByDeliveryType() {
            Page<InventoryIssue> page = new PageImpl<>(List.of(delivery));
            when(inventoryIssueRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(page);
            when(stockDeliveryMapper.toResponse(any(InventoryIssue.class))).thenReturn(response);

            Page<StockDeliveryResponse> result = stockDeliveryService.getAllStockDeliveries(
                    null, null, "ORDER", null, null, null, PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("Should filter by status")
        void shouldFilterByStatus() {
            Page<InventoryIssue> page = new PageImpl<>(List.of(delivery));
            when(inventoryIssueRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(page);
            when(stockDeliveryMapper.toResponse(any(InventoryIssue.class))).thenReturn(response);

            Page<StockDeliveryResponse> result = stockDeliveryService.getAllStockDeliveries(
                    null, "DRAFT", null, null, null, null, PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(1);
        }
    }

    // =========================================================
    // GET BY ID TESTS
    // =========================================================

    @Nested
    @DisplayName("getStockDeliveryById() Tests")
    class GetByIdTests {

        @Test
        @DisplayName("Should return delivery by ID")
        void shouldReturnDeliveryById() {
            when(inventoryIssueRepository.findByIdWithDetails(deliveryId))
                    .thenReturn(Optional.of(delivery));
            when(stockDeliveryMapper.toResponse(delivery)).thenReturn(response);

            StockDeliveryResponse result = stockDeliveryService.getStockDeliveryById(deliveryId);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(deliveryId);
        }

        @Test
        @DisplayName("Should throw when delivery not found")
        void shouldThrowWhenDeliveryNotFound() {
            when(inventoryIssueRepository.findByIdWithDetails(deliveryId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> stockDeliveryService.getStockDeliveryById(deliveryId))
                    .isInstanceOf(AppException.class);
        }
    }

    // =========================================================
    // GET STATISTICS TESTS
    // =========================================================

    @Nested
    @DisplayName("getDeliveryStatistics() Tests")
    class GetStatisticsTests {

        @Test
        @DisplayName("Should return delivery statistics")
        void shouldReturnDeliveryStatistics() {
            when(inventoryIssueRepository.count()).thenReturn(10L);
            when(inventoryIssueRepository.countByStatus("CONFIRMED")).thenReturn(5L);
            when(inventoryIssueRepository.countByStatus("DRAFT")).thenReturn(3L);
            when(inventoryIssueRepository.countByStatus("CANCELLED")).thenReturn(2L);
            when(inventoryIssueRepository.countByIssueType("ORDER")).thenReturn(5L);
            when(inventoryIssueRepository.countByIssueType("ADJUSTMENT")).thenReturn(2L);
            when(inventoryIssueRepository.countByIssueType("DISPOSAL")).thenReturn(1L);
            when(inventoryIssueRepository.countByIssueType("TRANSFER")).thenReturn(2L);
            when(inventoryIssueRepository.countDeliveries()).thenReturn(10L);
            when(inventoryIssueRepository.sumTotalCost()).thenReturn(BigDecimal.valueOf(1000000));

            Object result = stockDeliveryService.getDeliveryStatistics();

            assertThat(result).isNotNull();
        }
    }
}
