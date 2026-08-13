package fu.osms.inventory.service.impl;

import fu.osms.auth.entity.User;
import fu.osms.auth.repository.UserRepository;
import fu.osms.catalog.repository.ProductVariantRepository;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.inventory.dto.request.StocktakeSessionRequest;
import fu.osms.inventory.dto.response.StocktakeSessionResponse;
import fu.osms.inventory.entity.StocktakeSession;
import fu.osms.inventory.entity.Warehouse;
import fu.osms.inventory.mapper.StocktakeMapper;
import fu.osms.inventory.repository.InventoryItemRepository;
import fu.osms.inventory.repository.InventoryTransactionRepository;
import fu.osms.inventory.repository.StocktakeItemRepository;
import fu.osms.inventory.repository.StocktakeSessionRepository;
import fu.osms.inventory.repository.WarehouseRepository;
import fu.osms.inventory.service.InventoryAlertService;
import fu.osms.sync.service.MarketplaceInventoryPropagationService;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("StocktakeServiceImpl - Unit Tests")
class StocktakeServiceImplTest {

    @Mock private StocktakeSessionRepository sessionRepository;
    @Mock private StocktakeItemRepository itemRepository;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private ProductVariantRepository variantRepository;
    @Mock private InventoryItemRepository inventoryItemRepository;
    @Mock private InventoryTransactionRepository transactionRepository;
    @Mock private UserRepository userRepository;
    @Mock private StocktakeMapper mapper;
    @Mock private InventoryAlertService inventoryAlertService;
    @Mock private MarketplaceInventoryPropagationService marketplaceInventoryPropagationService;
    @Mock private ChannelProductVariantRepository channelProductVariantRepository;

    @InjectMocks
    private StocktakeServiceImpl stocktakeService;

    private StocktakeSession sample;
    private Warehouse warehouse;

    @BeforeEach
    void setUp() {
        warehouse = Warehouse.builder()
                .id(UUID.randomUUID())
                .name("Kho HCM")
                .isActive(true)
                .build();
        sample = StocktakeSession.builder()
                .id(UUID.randomUUID())
                .sessionCode("KK0001")
                .warehouse(warehouse)
                .status("DRAFT")
                .scheduledDate(LocalDate.now())
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("getStatistics - aggregates counts per status")
    void getStatistics_aggregates() {
        when(sessionRepository.count()).thenReturn(50L);
        when(sessionRepository.countByStatus("DRAFT")).thenReturn(15L);
        when(sessionRepository.countByStatus("IN_PROGRESS")).thenReturn(5L);
        when(sessionRepository.countByStatus("COMPLETED")).thenReturn(25L);
        when(sessionRepository.countByStatus("CANCELLED")).thenReturn(5L);

        Map<String, Object> stats = stocktakeService.getStatistics();

        assertThat(stats).containsEntry("totalCount", 50L)
                .containsEntry("draftCount", 15L)
                .containsEntry("inProgressCount", 5L)
                .containsEntry("completedCount", 25L)
                .containsEntry("cancelledCount", 5L);
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("getStocktakes - delegates to repository with Specification and maps results")
    void getStocktakes_returnsPagedMapped() {
        Pageable pageable = PageRequest.of(0, 10);
        StocktakeSessionResponse dto = StocktakeSessionResponse.builder()
                .id(sample.getId()).sessionCode("KK0001").status("DRAFT").build();
        Page<StocktakeSession> page = new PageImpl<>(List.of(sample), pageable, 1);

        when(sessionRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(mapper.toResponse(sample)).thenReturn(dto);

        Page<StocktakeSessionResponse> result =
                stocktakeService.getStocktakes(warehouse.getId(), "DRAFT", "KK", pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getSessionCode()).isEqualTo("KK0001");
        verify(sessionRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("getStocktakeById - throws AppException when session not found")
    void getStocktakeById_notFoundThrows() {
        UUID id = UUID.randomUUID();
        when(sessionRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stocktakeService.getStocktakeById(id))
                .isInstanceOf(AppException.class)
                .hasMessageContaining(ErrorCode.STOCKTAKE_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("getStocktakeById - returns mapped DTO when found")
    void getStocktakeById_succeeds() {
        when(sessionRepository.findById(sample.getId())).thenReturn(Optional.of(sample));
        when(mapper.toResponse(sample)).thenReturn(
                StocktakeSessionResponse.builder().id(sample.getId()).sessionCode("KK0001").build()
        );

        StocktakeSessionResponse res = stocktakeService.getStocktakeById(sample.getId());

        assertThat(res.getSessionCode()).isEqualTo("KK0001");
    }

    @Test
    @DisplayName("updateStocktake - throws CONFLICT when session is COMPLETED")
    void updateStocktake_completedForbidden() {
        sample.setStatus("COMPLETED");
        when(sessionRepository.findById(sample.getId())).thenReturn(Optional.of(sample));

        StocktakeSessionRequest req = StocktakeSessionRequest.builder()
                .warehouseId(warehouse.getId()).build();

        assertThatThrownBy(() -> stocktakeService.updateStocktake(sample.getId(), req))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Completed or cancelled stocktake");

        verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateStocktake - throws CONFLICT when session is CANCELLED")
    void updateStocktake_cancelledForbidden() {
        sample.setStatus("CANCELLED");
        when(sessionRepository.findById(sample.getId())).thenReturn(Optional.of(sample));

        StocktakeSessionRequest req = StocktakeSessionRequest.builder()
                .warehouseId(warehouse.getId()).build();

        assertThatThrownBy(() -> stocktakeService.updateStocktake(sample.getId(), req))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Completed or cancelled stocktake");
    }

    @Test
    @DisplayName("changeStatus - throws CONFLICT when session already COMPLETED")
    void changeStatus_completedForbidden() {
        sample.setStatus("COMPLETED");
        when(sessionRepository.findById(sample.getId())).thenReturn(Optional.of(sample));

        assertThatThrownBy(() -> stocktakeService.changeStatus(sample.getId(), "DRAFT"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Completed or cancelled stocktake cannot change status");
    }

    @Test
    @DisplayName("changeStatus - throws CONFLICT when session already CANCELLED")
    void changeStatus_cancelledForbidden() {
        sample.setStatus("CANCELLED");
        when(sessionRepository.findById(sample.getId())).thenReturn(Optional.of(sample));

        assertThatThrownBy(() -> stocktakeService.changeStatus(sample.getId(), "IN_PROGRESS"))
                .isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("createStocktake - throws WAREHOUSE_NOT_FOUND when warehouse missing")
    void createStocktake_missingWarehouseThrows() {
        UUID warehouseId = UUID.randomUUID();
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.empty());

        StocktakeSessionRequest req = StocktakeSessionRequest.builder()
                .warehouseId(warehouseId).build();

        assertThatThrownBy(() -> stocktakeService.createStocktake(req, false))
                .isInstanceOf(AppException.class)
                .hasMessageContaining(ErrorCode.WAREHOUSE_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("createStocktake - empty items list triggers VALIDATION_FAILED")
    void createStocktake_draftSucceeds() {
        // ensure SecurityContext has an authenticated user so createStocktake reaches the items-validation branch
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("op@osms.vn", null));
        User u = User.builder().id(UUID.randomUUID()).email("op@osms.vn").fullName("Ng").build();
        when(warehouseRepository.findById(warehouse.getId())).thenReturn(Optional.of(warehouse));
        when(userRepository.findByEmail("op@osms.vn")).thenReturn(Optional.of(u));
        when(sessionRepository.save(any(StocktakeSession.class))).thenAnswer(inv -> {
            StocktakeSession s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        StocktakeSessionRequest req = StocktakeSessionRequest.builder()
                .warehouseId(warehouse.getId()).items(List.of()).build();

        assertThatThrownBy(() -> stocktakeService.createStocktake(req, false))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("must include at least one item");
    }

    @Test
    @DisplayName("syncPendingMarketplaceInventory - returns 0 when no pending variants")
    void syncPendingMarketplaceInventory_noPendingReturnsZero() {
        when(itemRepository.findCompletedVariantIdsPendingMarketplaceSync()).thenReturn(List.of());

        int count = stocktakeService.syncPendingMarketplaceInventory();

        assertThat(count).isZero();
        verify(marketplaceInventoryPropagationService, never()).pushAvailableStock(any());
    }

    @Test
    @DisplayName("syncPendingMarketplaceInventory - pushes available stock for pending variants")
    void syncPendingMarketplaceInventory_pushesStock() {
        UUID variantId = UUID.randomUUID();
        when(itemRepository.findCompletedVariantIdsPendingMarketplaceSync()).thenReturn(List.of(variantId));

        int count = stocktakeService.syncPendingMarketplaceInventory();

        assertThat(count).isEqualTo(1);
        verify(marketplaceInventoryPropagationService).pushAvailableStock(List.of(variantId));
    }

    @Test
    @DisplayName("syncStocktakeMarketplaceInventory - throws INVALID_REQUEST when session not completed")
    void syncStocktakeMarketplaceInventory_notCompletedThrows() {
        sample.setStatus("IN_PROGRESS");
        when(sessionRepository.findById(sample.getId())).thenReturn(Optional.of(sample));

        assertThatThrownBy(() -> stocktakeService.syncStocktakeMarketplaceInventory(sample.getId()))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("mới được đồng bộ");
    }

    @Test
    @DisplayName("syncStocktakeMarketplaceInventory - pushes available stock for completed session")
    void syncStocktakeMarketplaceInventory_pushesStock() {
        sample.setStatus("COMPLETED");
        UUID variantId = UUID.randomUUID();
        when(sessionRepository.findById(sample.getId())).thenReturn(Optional.of(sample));
        when(itemRepository.findCompletedVariantIdsBySessionId(sample.getId())).thenReturn(List.of(variantId));

        int count = stocktakeService.syncStocktakeMarketplaceInventory(sample.getId());

        assertThat(count).isEqualTo(1);
        verify(marketplaceInventoryPropagationService).pushAvailableStock(List.of(variantId));
    }
}
