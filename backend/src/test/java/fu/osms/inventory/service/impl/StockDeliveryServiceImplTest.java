package fu.osms.inventory.service.impl;

import fu.osms.auth.entity.User;
import fu.osms.auth.repository.UserRepository;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.catalog.repository.ProductVariantRepository;
import fu.osms.common.exception.AppException;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.inventory.dto.request.StockDeliveryFromReceiptItemRequest;
import fu.osms.inventory.dto.request.StockDeliveryFromReceiptRequest;
import fu.osms.inventory.dto.request.StockDeliveryItemRequest;
import fu.osms.inventory.dto.request.StockDeliveryRequest;
import fu.osms.inventory.dto.response.StockDeliveryResponse;
import fu.osms.inventory.entity.*;
import fu.osms.inventory.mapper.StockDeliveryMapper;
import fu.osms.inventory.repository.*;
import fu.osms.inventory.service.InventoryAlertService;
import fu.osms.inventory.service.OrderGiftReservationService;
import fu.osms.sync.service.MarketplaceInventoryPropagationService;
import fu.osms.sync.service.MarketplaceWarehouseConsistencyService;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
    private StockReceiveRepository stockReceiveRepository;
    @Mock
    private StockReceiveItemRepository stockReceiveItemRepository;
    @Mock
    private ChannelProductVariantRepository channelProductVariantRepository;
    @Mock
    private StockDeliveryMapper stockDeliveryMapper;
    @Mock
    private InventoryAlertService inventoryAlertService;
    @Mock
    private OrderGiftReservationService orderGiftReservationService;
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
    private UUID receiptId;

    private Warehouse warehouse;
    private Warehouse inactiveWarehouse;
    private ProductVariant variant;
    private User user;
    private InventoryIssue delivery;
    private StockDeliveryResponse response;
    private Supplier supplier;
    private InventoryReceipt receipt;
    private InventoryReceiptItem receiptItem;

    @BeforeEach
    void setUp() {
        warehouseId = UUID.randomUUID();
        variantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        deliveryId = UUID.randomUUID();
        inventoryItemId = UUID.randomUUID();
        receiptId = UUID.randomUUID();

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

        supplier = Supplier.builder()
                .id(UUID.randomUUID())
                .name("NCC Test")
                .build();

        receipt = InventoryReceipt.builder()
                .id(receiptId)
                .warehouse(warehouse)
                .supplier(supplier)
                .receiptCode("PN-2026-001")
                .status("CONFIRMED")
                .build();

        receiptItem = InventoryReceiptItem.builder()
                .id(UUID.randomUUID())
                .receipt(receipt)
                .variant(variant)
                .quantity(10)
                .unitCost(BigDecimal.TEN)
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

    // =========================================================
    // CREATE FROM RECEIPT TESTS
    // =========================================================

    @Nested
    @DisplayName("createStockDeliveryFromReceipt() Tests")
    class CreateFromReceiptTests {

        @Test
        @DisplayName("Should throw when receipt not found")
        void shouldThrowWhenReceiptNotFound() {
            when(stockReceiveRepository.findById(receiptId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> stockDeliveryService.createStockDeliveryFromReceipt(receiptId, null))
                    .isInstanceOf(AppException.class);
        }

        @Test
        @DisplayName("Should throw when receipt is not confirmed")
        void shouldThrowWhenReceiptNotConfirmed() {
            receipt.setStatus("DRAFT");
            when(stockReceiveRepository.findById(receiptId)).thenReturn(Optional.of(receipt));

            assertThatThrownBy(() -> stockDeliveryService.createStockDeliveryFromReceipt(receiptId, null))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("xác nhận");
        }

        @Test
        @DisplayName("Should create TRANSFER delivery from a confirmed receipt")
        void shouldCreateTransferDeliveryFromReceipt() {
            when(stockReceiveRepository.findById(receiptId)).thenReturn(Optional.of(receipt));
            when(stockReceiveItemRepository.findByReceiptId(receiptId)).thenReturn(List.of(receiptItem));

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("test@osms.vn", "pass"));
            when(userRepository.findByEmail("test@osms.vn")).thenReturn(Optional.of(user));
            when(marketplaceWarehouseConsistencyService.resolveMasterWarehouse()).thenReturn(warehouse);
            when(inventoryIssueRepository.findByIssueCodeStartingWithOrderByIssueCodeDesc(anyString()))
                    .thenReturn(List.of());
            when(inventoryIssueRepository.save(any(InventoryIssue.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(productVariantRepository.findById(variantId)).thenReturn(Optional.of(variant));
            when(channelProductVariantRepository.findActiveByVariantIdWithChannel(variantId)).thenReturn(List.of());

            InventoryItem inventoryItem = InventoryItem.builder()
                    .id(inventoryItemId)
                    .warehouse(warehouse)
                    .variant(variant)
                    .quantityOnHand(100)
                    .reservedQuantity(0)
                    .averageCost(BigDecimal.TEN)
                    .build();
            when(inventoryItemRepository.findByWarehouseIdAndVariantId(any(), any()))
                    .thenReturn(Optional.empty());
            when(inventoryItemRepository.findByWarehouseIdAndVariantIdWithLock(any(), any()))
                    .thenReturn(Optional.of(inventoryItem));
            when(inventoryItemRepository.save(any(InventoryItem.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(inventoryTransactionRepository.save(any(InventoryTransaction.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            when(inventoryIssueRepository.findByIdWithDetails(any(UUID.class)))
                    .thenReturn(Optional.of(delivery));
            when(stockDeliveryMapper.toResponse(any(InventoryIssue.class))).thenReturn(response);

            StockDeliveryResponse result = stockDeliveryService.createStockDeliveryFromReceipt(receiptId, null);

            assertThat(result).isNotNull();

            ArgumentCaptor<InventoryIssue> issueCaptor = ArgumentCaptor.forClass(InventoryIssue.class);
            verify(inventoryIssueRepository, atLeastOnce()).save(issueCaptor.capture());
            assertThat(issueCaptor.getAllValues())
                    .anyMatch(issue -> receiptId.toString().equals(issue.getDocumentReferenceId()));
        }

        @Test
        @DisplayName("Should apply quantity overrides when building request from receipt")
        void shouldApplyQuantityOverrides() {
            StockDeliveryFromReceiptRequest request = new StockDeliveryFromReceiptRequest();
            request.setRecipient("Cty ABC");
            request.setNote("Tru lai hang loi");
            request.setItems(List.of(new StockDeliveryFromReceiptItemRequest(variantId, 3, "tra lai 3")));

            StockDeliveryRequest result = stockDeliveryService.buildRequestFromReceipt(
                    receipt, List.of(receiptItem), request);

            assertThat(result.getDeliveryType()).isEqualTo("TRANSFER");
            assertThat(result.getRecipient()).isEqualTo("Cty ABC");
            assertThat(result.getItems()).hasSize(1);
            assertThat(result.getItems().get(0).getProductVariantId()).isEqualTo(variantId);
            assertThat(result.getItems().get(0).getQuantity()).isEqualTo(3);
        }

        @Test
        @DisplayName("Should copy full receipt quantities when no overrides provided")
        void shouldCopyFullQuantitiesWithoutOverrides() {
            StockDeliveryRequest result = stockDeliveryService.buildRequestFromReceipt(
                    receipt, List.of(receiptItem), null);

            assertThat(result.getItems()).hasSize(1);
            assertThat(result.getItems().get(0).getQuantity()).isEqualTo(10);
            assertThat(result.getRecipient()).isEqualTo("NCC Test");
            assertThat(result.getNote()).contains(receipt.getReceiptCode());
        }

        @Test
        @DisplayName("Should throw when override references a variant not in the receipt")
        void shouldThrowWhenOverrideVariantNotInReceipt() {
            StockDeliveryFromReceiptRequest request = new StockDeliveryFromReceiptRequest();
            request.setItems(List.of(new StockDeliveryFromReceiptItemRequest(UUID.randomUUID(), 3, null)));

            assertThatThrownBy(() -> stockDeliveryService.buildRequestFromReceipt(
                    receipt, List.of(receiptItem), request))
                    .isInstanceOf(AppException.class);
        }

        @Test
        @DisplayName("Should throw when receipt has no items")
        void shouldThrowWhenReceiptHasNoItems() {
            assertThatThrownBy(() -> stockDeliveryService.buildRequestFromReceipt(receipt, List.of(), null))
                    .isInstanceOf(AppException.class);
        }
    }
}
