package fu.osms.inventory.service.impl;

import fu.osms.auth.entity.User;
import fu.osms.auth.repository.UserRepository;
import fu.osms.auth.repository.UserRoleRepository;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.catalog.repository.ProductVariantRepository;
import fu.osms.inventory.dto.request.StockTransferItemRequest;
import fu.osms.inventory.dto.request.StockTransferRequest;
import fu.osms.inventory.dto.response.TransferInventoryListResponse;
import fu.osms.inventory.dto.response.TransferSummaryDTO;
import fu.osms.inventory.entity.InventoryItem;
import fu.osms.inventory.entity.StockTransfer;
import fu.osms.inventory.entity.StockTransferItem;
import fu.osms.inventory.entity.Warehouse;
import fu.osms.inventory.mapper.StockTransferMapper;
import fu.osms.inventory.repository.*;
import fu.osms.notification.service.NotificationService;
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
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("StokeTransferServiceImpl Tests")
class StokeTransferServiceImplTest {

    @Mock
    private StockTransferRepository transferRepository;
    @Mock
    private StockTransferMapper transferMapper;
    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private InventoryItemRepository inventoryItemRepository;
    @Mock
    private InventoryTransactionRepository transactionRepository;
    @Mock
    private ProductVariantRepository variantRepository;
    @Mock
    private StokeTransferItemRepository stokeTransferItemRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserRoleRepository userRoleRepository;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private StokeTransferServiceImpl stockTransferService;

    // Test data
    private UUID fromWarehouseId;
    private UUID toWarehouseId;
    private UUID variantId;
    private UUID userId;
    private UUID transferId;

    private Warehouse fromWarehouse;
    private Warehouse toWarehouse;
    private ProductVariant variant;
    private User user;
    private InventoryItem inventoryItem;
    private StockTransfer transfer;
    private StockTransferRequest request;
    private StockTransferItemRequest itemRequest;

    @BeforeEach
    void setUp() {
        fromWarehouseId = UUID.randomUUID();
        toWarehouseId = UUID.randomUUID();
        variantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        transferId = UUID.randomUUID();

        fromWarehouse = Warehouse.builder()
                .id(fromWarehouseId)
                .name("From Warehouse")
                .isActive(true)
                .build();

        toWarehouse = Warehouse.builder()
                .id(toWarehouseId)
                .name("To Warehouse")
                .isActive(true)
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

        inventoryItem = InventoryItem.builder()
                .id(UUID.randomUUID())
                .warehouse(fromWarehouse)
                .variant(variant)
                .quantityOnHand(100)
                .reservedQuantity(0)
                .availableQuantity(100)
                .build();

        transfer = StockTransfer.builder()
                .id(transferId)
                .fromWarehouse(fromWarehouse)
                .toWarehouse(toWarehouse)
                .transferCode("TRANS-20240101-0001")
                .status("DRAFT")
                .createdBy(user)
                .createdAt(OffsetDateTime.now())
                .items(new ArrayList<>())
                .build();

        itemRequest = new StockTransferItemRequest();
        itemRequest.setVariantId(variantId);
        itemRequest.setQuantity(10);

        request = new StockTransferRequest();
        request.setFromWarehouseId(fromWarehouseId);
        request.setToWarehouseId(toWarehouseId);
        request.setTransferCode("TRANS-20240101-0001");
        request.setTransferTime(OffsetDateTime.now());
        request.setStatus("DRAFT");
        request.setCreatedById(userId);
        request.setItems(List.of(itemRequest));
    }

    // =========================================================
    // CREATE TRANSFER TESTS
    // =========================================================

    @Nested
    @DisplayName("createTransferInventory() Tests")
    class CreateTransferTests {

        @Test
        @DisplayName("Should create DRAFT transfer successfully")
        void shouldCreateDraftTransferSuccessfully() {
            request.setStatus("DRAFT");
            when(transferMapper.toEntity(request)).thenReturn(transfer);
            when(warehouseRepository.getReferenceById(fromWarehouseId)).thenReturn(fromWarehouse);
            when(warehouseRepository.getReferenceById(toWarehouseId)).thenReturn(toWarehouse);
            when(userRepository.findUserById(userId)).thenReturn(user);
            when(transferMapper.toItemEntity(itemRequest)).thenReturn(
                    StockTransferItem.builder().quantity(10).build());
            when(variantRepository.getReferenceById(variantId)).thenReturn(variant);
            when(transferRepository.save(any(StockTransfer.class))).thenReturn(transfer);

            stockTransferService.createTransferInventory(request);

            verify(transferRepository).save(any(StockTransfer.class));
        }

        @Test
        @DisplayName("Should throw when same warehouse for from and to")
        void shouldThrowWhenSameWarehouse() {
            request.setFromWarehouseId(toWarehouseId); // Same as toWarehouseId

            assertThatThrownBy(() -> stockTransferService.createTransferInventory(request))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // =========================================================
    // GET TRANSFER LIST TESTS
    // =========================================================

    @Nested
    @DisplayName("getTransferListData() Tests")
    class GetTransferListTests {

        @Test
        @DisplayName("Should return transfer list with pagination")
        void shouldReturnTransferListWithPagination() {
            TransferSummaryDTO summary = new TransferSummaryDTO(10, 5, 3, 2);
            Page<StockTransfer> page = new PageImpl<>(List.of(transfer), PageRequest.of(0, 10), 1);

            when(transferRepository.getTransferSummary()).thenReturn(summary);
            when(transferRepository.searchTransfers(any(), any(), any(), any(Pageable.class))).thenReturn(page);

            TransferInventoryListResponse result = stockTransferService.getTransferListData(null, null, null, 0, 10);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should filter by status")
        void shouldFilterByStatus() {
            TransferSummaryDTO summary = new TransferSummaryDTO(0, 0, 0, 0);
            Page<StockTransfer> page = new PageImpl<>(List.of(transfer), PageRequest.of(0, 10), 1);

            when(transferRepository.getTransferSummary()).thenReturn(summary);
            when(transferRepository.searchTransfers(eq("DRAFT"), any(), any(), any(Pageable.class))).thenReturn(page);

            TransferInventoryListResponse result = stockTransferService.getTransferListData("DRAFT", null, null, 0, 10);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should handle null summary")
        void shouldHandleNullSummary() {
            Page<StockTransfer> page = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);

            when(transferRepository.getTransferSummary()).thenReturn(null);
            when(transferRepository.searchTransfers(any(), any(), any(), any(Pageable.class))).thenReturn(page);

            TransferInventoryListResponse result = stockTransferService.getTransferListData(null, null, null, 0, 10);

            assertThat(result).isNotNull();
        }
    }

    // =========================================================
    // GENERATE CODE TEST
    // =========================================================

    @Nested
    @DisplayName("generateTransferCode() Tests")
    class GenerateCodeTests {

        @Test
        @DisplayName("Should generate new code when no existing transfers")
        void shouldGenerateNewCodeWhenNoExisting() {
            when(transferRepository.findLatestTransferCodeByPrefix(any())).thenReturn(Optional.empty());

            String code = stockTransferService.generateTransferCode();

            assertThat(code).isNotNull();
            assertThat(code).contains("Trans-");
        }

        @Test
        @DisplayName("Should increment code from last transfer")
        void shouldIncrementCodeFromLast() {
            when(transferRepository.findLatestTransferCodeByPrefix(any()))
                    .thenReturn(Optional.of("TRANS-01012024-0005"));

            String code = stockTransferService.generateTransferCode();

            assertThat(code).isNotNull();
        }
    }
}
