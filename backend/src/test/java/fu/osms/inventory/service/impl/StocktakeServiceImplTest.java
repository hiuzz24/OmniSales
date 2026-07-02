package fu.osms.inventory.service.impl;

import fu.osms.common.exception.AppException;
import fu.osms.inventory.dto.response.StocktakeSessionResponse;
import fu.osms.inventory.entity.StocktakeSession;
import fu.osms.inventory.entity.Warehouse;
import fu.osms.inventory.mapper.StocktakeMapper;
import fu.osms.inventory.repository.StocktakeItemRepository;
import fu.osms.inventory.repository.StocktakeSessionRepository;
import fu.osms.inventory.repository.WarehouseRepository;
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

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("StocktakeServiceImpl Tests")
class StocktakeServiceImplTest {

    @Mock
    private StocktakeSessionRepository sessionRepository;
    @Mock
    private StocktakeItemRepository itemRepository;
    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private StocktakeMapper mapper;
    @Mock
    private fu.osms.inventory.service.InventoryAlertService inventoryAlertService;

    @InjectMocks
    private StocktakeServiceImpl stocktakeService;

    // Test data
    private UUID warehouseId;
    private UUID sessionId;
    private Warehouse warehouse;
    private StocktakeSession session;
    private StocktakeSessionResponse response;

    @BeforeEach
    void setUp() {
        warehouseId = UUID.randomUUID();
        sessionId = UUID.randomUUID();

        warehouse = Warehouse.builder()
                .id(warehouseId)
                .name("Test Warehouse")
                .isActive(true)
                .build();

        session = StocktakeSession.builder()
                .id(sessionId)
                .warehouse(warehouse)
                .sessionCode("KK-20240101-0001")
                .scheduledDate(LocalDate.now())
                .status("DRAFT")
                .build();

        response = StocktakeSessionResponse.builder()
                .id(sessionId)
                .sessionCode("KK-20240101-0001")
                .status("DRAFT")
                .build();

        lenient().when(mapper.toResponse(any(StocktakeSession.class))).thenReturn(response);
    }

    // =========================================================
    // GET STOCKTAKES TESTS
    // =========================================================

    @Nested
    @DisplayName("getStocktakes() Tests")
    class GetStocktakesTests {

        @Test
        @DisplayName("Should return paginated list of stocktakes")
        void shouldReturnPaginatedStocktakes() {
            Page<StocktakeSession> page = new PageImpl<>(List.of(session));
            when(sessionRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
            when(mapper.toResponse(any(StocktakeSession.class))).thenReturn(response);

            Page<StocktakeSessionResponse> result = stocktakeService.getStocktakes(
                    null, null, null, PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("Should filter by warehouse")
        void shouldFilterByWarehouse() {
            Page<StocktakeSession> page = new PageImpl<>(List.of(session));
            when(sessionRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
            when(mapper.toResponse(any(StocktakeSession.class))).thenReturn(response);

            Page<StocktakeSessionResponse> result = stocktakeService.getStocktakes(
                    warehouseId, null, null, PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("Should filter by status")
        void shouldFilterByStatus() {
            Page<StocktakeSession> page = new PageImpl<>(List.of(session));
            when(sessionRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
            when(mapper.toResponse(any(StocktakeSession.class))).thenReturn(response);

            Page<StocktakeSessionResponse> result = stocktakeService.getStocktakes(
                    null, "DRAFT", null, PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(1);
        }
    }

    // =========================================================
    // GET BY ID TESTS
    // =========================================================

    @Nested
    @DisplayName("getStocktakeById() Tests")
    class GetByIdTests {

        @Test
        @DisplayName("Should return stocktake by ID")
        void shouldReturnStocktakeById() {
            when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
            when(mapper.toResponse(session)).thenReturn(response);

            StocktakeSessionResponse result = stocktakeService.getStocktakeById(sessionId);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(sessionId);
        }

        @Test
        @DisplayName("Should throw when stocktake not found")
        void shouldThrowWhenStocktakeNotFound() {
            when(sessionRepository.findById(sessionId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> stocktakeService.getStocktakeById(sessionId))
                    .isInstanceOf(AppException.class);
        }
    }

    // =========================================================
    // UPDATE STOCKTAKE TESTS
    // =========================================================

    @Nested
    @DisplayName("updateStocktake() Tests")
    class UpdateStocktakeTests {

        @Test
        @DisplayName("Should throw when updating COMPLETED stocktake")
        void shouldThrowWhenUpdatingCompleted() {
            session.setStatus("COMPLETED");
            when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

            assertThatThrownBy(() -> stocktakeService.updateStocktake(sessionId, null))
                    .isInstanceOf(AppException.class);
        }

        @Test
        @DisplayName("Should throw when updating CANCELLED stocktake")
        void shouldThrowWhenUpdatingCancelled() {
            session.setStatus("CANCELLED");
            when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

            assertThatThrownBy(() -> stocktakeService.updateStocktake(sessionId, null))
                    .isInstanceOf(AppException.class);
        }
    }

    // =========================================================
    // CHANGE STATUS TESTS
    // =========================================================

    @Nested
    @DisplayName("changeStatus() Tests")
    class ChangeStatusTests {

        @Test
        @DisplayName("Should throw when changing COMPLETED stocktake status")
        void shouldThrowWhenChangingCompletedStatus() {
            session.setStatus("COMPLETED");
            when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

            assertThatThrownBy(() -> stocktakeService.changeStatus(sessionId, "CANCELLED"))
                    .isInstanceOf(AppException.class);
        }

        @Test
        @DisplayName("Should throw when changing CANCELLED stocktake status")
        void shouldThrowWhenChangingCancelledStatus() {
            session.setStatus("CANCELLED");
            when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

            assertThatThrownBy(() -> stocktakeService.changeStatus(sessionId, "IN_PROGRESS"))
                    .isInstanceOf(AppException.class);
        }
    }

    // =========================================================
    // GET STATISTICS TESTS
    // =========================================================

    @Nested
    @DisplayName("getStatistics() Tests")
    class GetStatisticsTests {

        @Test
        @DisplayName("Should return stocktake statistics")
        void shouldReturnStatistics() {
            when(sessionRepository.count()).thenReturn(10L);
            when(sessionRepository.countByStatus("COMPLETED")).thenReturn(5L);
            when(sessionRepository.countByStatus("IN_PROGRESS")).thenReturn(3L);
            when(sessionRepository.countByStatus("DRAFT")).thenReturn(2L);

            Map<String, Object> result = stocktakeService.getStatistics();

            assertThat(result).isNotNull();
            assertThat(result).containsKey("totalCount");
            assertThat(result).containsKey("completedCount");
        }
    }
}
