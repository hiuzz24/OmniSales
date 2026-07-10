package fu.osms.inventory.service.impl;

import fu.osms.common.dto.PageResponse;
import fu.osms.inventory.dto.response.InventoryTransactionDTO;
import fu.osms.inventory.dto.response.InventoryTransactionResponse;
import fu.osms.inventory.entity.InventoryTransaction;
import fu.osms.inventory.entity.Warehouse;
import fu.osms.inventory.enums.InvTxnType;
import fu.osms.inventory.mapper.InventoryTransactionDTOMapper;
import fu.osms.inventory.mapper.InventoryTransactionMapper;
import fu.osms.inventory.repository.InventoryTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryTransactionServiceImpl Tests")
class InventoryTransactionServiceImplTest {

    @Mock
    private InventoryTransactionRepository inventoryTransactionRepository;

    @Mock
    private InventoryTransactionDTOMapper inventoryTransactionDTOMapper;

    @Mock
    private InventoryTransactionMapper inventoryTransactionMapper;

    @InjectMocks
    private InventoryTransactionServiceImpl inventoryTransactionService;

    private UUID transactionId;
    private UUID warehouseId;
    private UUID variantId;
    private InventoryTransaction testTransaction;
    private InventoryTransactionDTO testDto;
    private InventoryTransactionResponse testResponse;

    @BeforeEach
    void setUp() {
        transactionId = UUID.randomUUID();
        warehouseId = UUID.randomUUID();
        variantId = UUID.randomUUID();

        Warehouse warehouse = Warehouse.builder()
                .id(warehouseId)
                .name("Main Warehouse")
                .build();

        testTransaction = InventoryTransaction.builder()
                .id(transactionId)
                .warehouse(warehouse)
                .variant(fu.osms.catalog.entity.ProductVariant.builder().id(variantId).sku("SKU-001").build())
                .type(InvTxnType.IMPORT)
                .quantityChange(100)
                .quantityBefore(0)
                .quantityAfter(100)
                .unitCost(BigDecimal.valueOf(10.00))
                .performedAt(OffsetDateTime.now())
                .build();

        testDto = InventoryTransactionDTO.builder()
                .id(transactionId)
                .warehouseId(warehouseId)
                .warehouseName("Main Warehouse")
                .variantId(variantId)
                .variantSku("SKU-001")
                .type(InvTxnType.IMPORT)
                .typeLabel("Nhập kho")
                .quantityChange(100)
                .quantityBefore(0)
                .quantityAfter(100)
                .performedAt(OffsetDateTime.now())
                .build();

        testResponse = InventoryTransactionResponse.builder()
                .id(transactionId)
                .warehouseId(warehouseId)
                .warehouseName("Main Warehouse")
                .variantId(variantId)
                .variantSku("SKU-001")
                .type(InvTxnType.IMPORT)
                .quantityChange(100)
                .quantityBefore(0)
                .quantityAfter(100)
                .performedAt(OffsetDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("getTransactionsDTO() Tests")
    class GetTransactionsDTOTests {

        @Test
        @DisplayName("Should return paginated transactions with correct metadata")
        void getTransactionsDTO_success() {
            PageRequest pageRequest = PageRequest.of(0, 10);
            List<InventoryTransaction> transactions = List.of(testTransaction);
            Page<InventoryTransaction> page = new PageImpl<>(transactions, pageRequest, 1);

            when(inventoryTransactionRepository.findAllWithDetails(pageRequest)).thenReturn(page);
            when(inventoryTransactionDTOMapper.toDto(testTransaction)).thenReturn(testDto);

            PageResponse<InventoryTransactionDTO> result = inventoryTransactionService.getTransactionsDTO(pageRequest, 0, 10);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getTotalPages()).isEqualTo(1);
            assertThat(result.isFirst()).isTrue();
            assertThat(result.isLast()).isTrue();
            verify(inventoryTransactionRepository).findAllWithDetails(pageRequest);
        }

        @Test
        @DisplayName("Should handle empty result page")
        void getTransactionsDTO_emptyResult() {
            PageRequest pageRequest = PageRequest.of(0, 10);
            Page<InventoryTransaction> emptyPage = new PageImpl<>(Collections.emptyList(), pageRequest, 0);

            when(inventoryTransactionRepository.findAllWithDetails(pageRequest)).thenReturn(emptyPage);

            PageResponse<InventoryTransactionDTO> result = inventoryTransactionService.getTransactionsDTO(pageRequest, 0, 10);

            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
            assertThat(result.getTotalPages()).isZero();
        }

        @Test
        @DisplayName("Should handle pagination metadata correctly")
        void getTransactionsDTO_pagination() {
            PageRequest pageRequest = PageRequest.of(0, 2);
            List<InventoryTransaction> transactions = List.of(testTransaction, testTransaction);
            Page<InventoryTransaction> page = new PageImpl<>(transactions, pageRequest, 5);

            when(inventoryTransactionRepository.findAllWithDetails(pageRequest)).thenReturn(page);
            when(inventoryTransactionDTOMapper.toDto(any())).thenReturn(testDto);

            PageResponse<InventoryTransactionDTO> result = inventoryTransactionService.getTransactionsDTO(pageRequest, 0, 2);

            assertThat(result.getTotalElements()).isEqualTo(5);
            assertThat(result.getTotalPages()).isEqualTo(3);
            assertThat(result.isFirst()).isTrue();
            assertThat(result.isLast()).isFalse();
        }
    }

    @Nested
    @DisplayName("getTransactionsDTOByVariant() Tests")
    class GetTransactionsDTOByVariantTests {

        @Test
        @DisplayName("Should return transactions filtered by variant")
        void getTransactionsDTOByVariant_success() {
            PageRequest pageRequest = PageRequest.of(0, 10);
            List<InventoryTransaction> transactions = List.of(testTransaction);
            Page<InventoryTransaction> page = new PageImpl<>(transactions, pageRequest, 1);

            when(inventoryTransactionRepository.findByVariantIdWithDetails(variantId, pageRequest)).thenReturn(page);
            when(inventoryTransactionDTOMapper.toDto(testTransaction)).thenReturn(testDto);

            PageResponse<InventoryTransactionDTO> result = inventoryTransactionService.getTransactionsDTOByVariant(variantId, pageRequest, 0, 10);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getVariantId()).isEqualTo(variantId);
            verify(inventoryTransactionRepository).findByVariantIdWithDetails(variantId, pageRequest);
        }

        @Test
        @DisplayName("Should handle empty result for variant")
        void getTransactionsDTOByVariant_emptyResult() {
            PageRequest pageRequest = PageRequest.of(0, 10);
            Page<InventoryTransaction> emptyPage = new PageImpl<>(Collections.emptyList(), pageRequest, 0);

            when(inventoryTransactionRepository.findByVariantIdWithDetails(variantId, pageRequest)).thenReturn(emptyPage);

            PageResponse<InventoryTransactionDTO> result = inventoryTransactionService.getTransactionsDTOByVariant(variantId, pageRequest, 0, 10);

            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
        }
    }

    @Nested
    @DisplayName("getInventoryLogs() Tests")
    class GetInventoryLogsTests {

        @Test
        @DisplayName("Should return all transactions when no filters applied")
        void getInventoryLogs_noFilters() {
            PageRequest expectedPageRequest = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "performedAt"));
            List<InventoryTransaction> transactions = List.of(testTransaction);
            Page<InventoryTransaction> page = new PageImpl<>(transactions, expectedPageRequest, 1);

            when(inventoryTransactionRepository.findAll(any(Specification.class), eq(expectedPageRequest))).thenReturn(page);
            when(inventoryTransactionMapper.toResponse(testTransaction)).thenReturn(testResponse);

            Page<InventoryTransactionResponse> result = inventoryTransactionService.getInventoryLogs(
                    null, null, null, null, null, null, 0, 10);

            assertThat(result.getContent()).hasSize(1);
            verify(inventoryTransactionRepository).findAll(any(Specification.class), eq(expectedPageRequest));
        }

        @Test
        @DisplayName("Should filter by warehouseId")
        void getInventoryLogs_withWarehouseFilter() {
            PageRequest expectedPageRequest = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "performedAt"));
            List<InventoryTransaction> transactions = List.of(testTransaction);
            Page<InventoryTransaction> page = new PageImpl<>(transactions, expectedPageRequest, 1);

            when(inventoryTransactionRepository.findAll(any(Specification.class), eq(expectedPageRequest))).thenReturn(page);
            when(inventoryTransactionMapper.toResponse(testTransaction)).thenReturn(testResponse);

            Page<InventoryTransactionResponse> result = inventoryTransactionService.getInventoryLogs(
                    warehouseId, null, null, null, null, null, 0, 10);

            assertThat(result.getContent()).hasSize(1);
            verify(inventoryTransactionRepository).findAll(any(Specification.class), eq(expectedPageRequest));
        }

        @Test
        @DisplayName("Should filter by transaction type")
        void getInventoryLogs_withTypeFilter() {
            PageRequest expectedPageRequest = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "performedAt"));
            List<InventoryTransaction> transactions = List.of(testTransaction);
            Page<InventoryTransaction> page = new PageImpl<>(transactions, expectedPageRequest, 1);

            when(inventoryTransactionRepository.findAll(any(Specification.class), eq(expectedPageRequest))).thenReturn(page);
            when(inventoryTransactionMapper.toResponse(testTransaction)).thenReturn(testResponse);

            Page<InventoryTransactionResponse> result = inventoryTransactionService.getInventoryLogs(
                    null, null, InvTxnType.IMPORT, null, null, null, 0, 10);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getType()).isEqualTo(InvTxnType.IMPORT);
        }

        @Test
        @DisplayName("Should filter by date range")
        void getInventoryLogs_withDateRange() {
            OffsetDateTime startDate = OffsetDateTime.now().minusDays(7);
            OffsetDateTime endDate = OffsetDateTime.now();
            PageRequest expectedPageRequest = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "performedAt"));
            List<InventoryTransaction> transactions = List.of(testTransaction);
            Page<InventoryTransaction> page = new PageImpl<>(transactions, expectedPageRequest, 1);

            when(inventoryTransactionRepository.findAll(any(Specification.class), eq(expectedPageRequest))).thenReturn(page);
            when(inventoryTransactionMapper.toResponse(testTransaction)).thenReturn(testResponse);

            Page<InventoryTransactionResponse> result = inventoryTransactionService.getInventoryLogs(
                    null, null, null, startDate, endDate, null, 0, 10);

            assertThat(result.getContent()).hasSize(1);
            verify(inventoryTransactionRepository).findAll(any(Specification.class), eq(expectedPageRequest));
        }

        @Test
        @DisplayName("Should filter by product search (SKU or name)")
        void getInventoryLogs_withProductSearch() {
            PageRequest expectedPageRequest = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "performedAt"));
            List<InventoryTransaction> transactions = List.of(testTransaction);
            Page<InventoryTransaction> page = new PageImpl<>(transactions, expectedPageRequest, 1);

            when(inventoryTransactionRepository.findAll(any(Specification.class), eq(expectedPageRequest))).thenReturn(page);
            when(inventoryTransactionMapper.toResponse(testTransaction)).thenReturn(testResponse);

            Page<InventoryTransactionResponse> result = inventoryTransactionService.getInventoryLogs(
                    null, "SKU-001", null, null, null, null, 0, 10);

            assertThat(result.getContent()).hasSize(1);
            verify(inventoryTransactionRepository).findAll(any(Specification.class), eq(expectedPageRequest));
        }

        @Test
        @DisplayName("Should combine all filters")
        void getInventoryLogs_withAllFilters() {
            UUID performedById = UUID.randomUUID();
            OffsetDateTime startDate = OffsetDateTime.now().minusDays(7);
            OffsetDateTime endDate = OffsetDateTime.now();
            PageRequest expectedPageRequest = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "performedAt"));
            List<InventoryTransaction> transactions = List.of(testTransaction);
            Page<InventoryTransaction> page = new PageImpl<>(transactions, expectedPageRequest, 1);

            when(inventoryTransactionRepository.findAll(any(Specification.class), eq(expectedPageRequest))).thenReturn(page);
            when(inventoryTransactionMapper.toResponse(testTransaction)).thenReturn(testResponse);

            Page<InventoryTransactionResponse> result = inventoryTransactionService.getInventoryLogs(
                    warehouseId, "test", InvTxnType.IMPORT, startDate, endDate, performedById, 0, 10);

            assertThat(result.getContent()).hasSize(1);
            verify(inventoryTransactionRepository).findAll(any(Specification.class), eq(expectedPageRequest));
        }

        @Test
        @DisplayName("Should handle empty result")
        void getInventoryLogs_emptyResult() {
            PageRequest expectedPageRequest = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "performedAt"));
            Page<InventoryTransaction> emptyPage = new PageImpl<>(Collections.emptyList(), expectedPageRequest, 0);

            when(inventoryTransactionRepository.findAll(any(Specification.class), eq(expectedPageRequest))).thenReturn(emptyPage);

            Page<InventoryTransactionResponse> result = inventoryTransactionService.getInventoryLogs(
                    null, null, null, null, null, null, 0, 10);

            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
        }

        @Test
        @DisplayName("Should handle pagination correctly")
        void getInventoryLogs_pagination() {
            PageRequest expectedPageRequest = PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "performedAt"));
            List<InventoryTransaction> transactions = List.of(testTransaction, testTransaction);
            Page<InventoryTransaction> page = new PageImpl<>(transactions, expectedPageRequest, 5);

            when(inventoryTransactionRepository.findAll(any(Specification.class), eq(expectedPageRequest))).thenReturn(page);
            when(inventoryTransactionMapper.toResponse(any())).thenReturn(testResponse);

            Page<InventoryTransactionResponse> result = inventoryTransactionService.getInventoryLogs(
                    null, null, null, null, null, null, 1, 2);

            assertThat(result.getTotalElements()).isEqualTo(5);
            assertThat(result.getTotalPages()).isEqualTo(3);
            assertThat(result.isFirst()).isFalse();
            assertThat(result.isLast()).isFalse();
        }
    }
}
