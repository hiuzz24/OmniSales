package fu.osms.inventory.service.impl;

import fu.osms.auth.entity.User;
import fu.osms.auth.repository.UserRepository;
import fu.osms.catalog.entity.Product;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.catalog.repository.ProductVariantRepository;
import fu.osms.common.dto.PageResponse;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.inventory.dto.request.StockReceiveItemRequest;
import fu.osms.inventory.dto.request.StockReceiveRequest;
import fu.osms.inventory.dto.response.StockReceiveItemResponse;
import fu.osms.inventory.dto.response.StockReceiveResponse;
import fu.osms.inventory.entity.*;
import fu.osms.inventory.enums.InvTxnType;
import fu.osms.inventory.mapper.StockReceiveMapper;
import fu.osms.inventory.repository.*;
import fu.osms.notification.service.NotificationService;
import fu.osms.purchase.entity.PurchaseOrder;
import fu.osms.purchase.entity.PurchaseOrderItem;
import fu.osms.purchase.enums.PurchaseOrderStatus;
import fu.osms.purchase.repository.PurchaseOrderRepository;
import fu.osms.purchase.service.PurchaseOrderService;
import fu.osms.channel.repository.ChannelProductVariantRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("StockReceiveServiceImpl Tests")
class StockReceiveServiceImplTest {

    @Mock
    private StockReceiveRepository stockReceiveRepository;
    @Mock
    private StockReceiveItemRepository stockReceiveItemRepository;
    @Mock
    private InventoryItemRepository inventoryItemRepository;
    @Mock
    private InventoryTransactionRepository inventoryTransactionRepository;
    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private SupplierRepository supplierRepository;
    @Mock
    private ProductVariantRepository variantRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private StockReceiveMapper receiptMapper;
    @Mock
    private MarketplaceInventoryPropagationService marketplaceInventoryPropagationService;
    @Mock
    private MarketplaceWarehouseConsistencyService marketplaceWarehouseConsistencyService;
    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;
    @Mock
    private PurchaseOrderService purchaseOrderService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private ChannelProductVariantRepository channelProductVariantRepository;

    @InjectMocks
    private StockReceiveServiceImpl stockReceiveService;

    // Test data
    private UUID warehouseId;
    private UUID supplierId;
    private UUID variantId;
    private UUID userId;
    private UUID receiptId;
    private UUID purchaseOrderId;

    private Warehouse warehouse;
    private Supplier supplier;
    private ProductVariant variant;
    private Product product;
    private User user;
    private InventoryReceipt receipt;
    private InventoryReceiptItem receiptItem;
    private InventoryItem inventoryItem;
    private StockReceiveRequest request;
    private StockReceiveItemRequest itemRequest;
    private StockReceiveResponse response;
    private StockReceiveItemResponse itemResponse;
    private PurchaseOrder purchaseOrder;
    private PurchaseOrderItem purchaseOrderItem;

    @BeforeEach
    void setUp() {
        warehouseId = UUID.randomUUID();
        supplierId = UUID.randomUUID();
        variantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        receiptId = UUID.randomUUID();
        purchaseOrderId = UUID.randomUUID();

        warehouse = Warehouse.builder()
                .id(warehouseId)
                .name("Main Warehouse")
                .isActive(true)
                .build();

        supplier = Supplier.builder()
                .id(supplierId)
                .name("Test Supplier")
                .build();

        product = Product.builder()
                .id(UUID.randomUUID())
                .name("Test Product")
                .build();

        variant = ProductVariant.builder()
                .id(variantId)
                .product(product)
                .sku("SKU001")
                .name("Test Variant")
                .isActive(true)
                .deletedAt(null)
                .build();

        user = User.builder()
                .id(userId)
                .email("testuser@example.com")
                .fullName("Test User")
                .build();

        inventoryItem = InventoryItem.builder()
                .id(UUID.randomUUID())
                .warehouse(warehouse)
                .variant(variant)
                .quantityOnHand(100)
                .averageCost(new BigDecimal("50.00"))
                .build();

        itemRequest = StockReceiveItemRequest.builder()
                .variantId(variantId)
                .quantity(10)
                .unitCost(new BigDecimal("60.00"))
                .notes("Test item")
                .build();

        // A receiving purchase order so the new validation passes
        purchaseOrder = PurchaseOrder.builder()
                .id(purchaseOrderId)
                .warehouse(warehouse)
                .supplier(supplier)
                .status(PurchaseOrderStatus.INSPECTED)
                .build();

        purchaseOrderItem = PurchaseOrderItem.builder()
                .id(UUID.randomUUID())
                .purchaseOrder(purchaseOrder)
                .variant(variant)
                .quantity(10)
                .unitCost(new BigDecimal("60.00"))
                .build();
        purchaseOrder.getItems().add(purchaseOrderItem);

        request = StockReceiveRequest.builder()
                .warehouseId(warehouseId)
                .supplierId(supplierId)
                .purchaseOrderId(purchaseOrderId)
                .invoiceNumber("INV001")
                .receivedAt(LocalDate.now())
                .notes("Test receipt")
                .items(List.of(itemRequest))
                .isDraft(false)
                .build();

        receipt = InventoryReceipt.builder()
                .id(receiptId)
                .warehouse(warehouse)
                .supplier(supplier)
                .purchaseOrder(purchaseOrder)
                .receiptCode("PN-2026-001")
                .invoiceNumber("PN-2026-001")
                .status("CONFIRMED")
                .totalCost(new BigDecimal("600.00"))
                .receivedAt(OffsetDateTime.now())
                .notes("Test receipt")
                .createdBy(user)
                .build();

        receiptItem = InventoryReceiptItem.builder()
                .id(UUID.randomUUID())
                .receipt(receipt)
                .variant(variant)
                .quantity(10)
                .unitCost(new BigDecimal("60.00"))
                .avgCostBefore(new BigDecimal("50.00"))
                .avgCostAfter(new BigDecimal("50.91"))
                .notes("Test item")
                .build();

        itemResponse = StockReceiveItemResponse.builder()
                .id(receiptItem.getId())
                .variantId(variantId)
                .variantSku("SKU001")
                .variantName("Test Variant")
                .quantity(10)
                .unitCost(new BigDecimal("60.00"))
                .build();

        response = StockReceiveResponse.builder()
                .id(receiptId)
                .warehouseId(warehouseId)
                .warehouseName("Main Warehouse")
                .supplierId(supplierId)
                .supplierName("Test Supplier")
                .receiptCode("PN-2026-001")
                .invoiceNumber("PN-2026-001")
                .status("CONFIRMED")
                .totalCost(new BigDecimal("600.00"))
                .items(List.of(itemResponse))
                .totalSkuCount(1)
                .totalQuantity(10)
                .build();

        // Default stubs for marketplace / enrichment calls
        lenient().when(channelProductVariantRepository.findActiveByVariantIdWithChannel(any(UUID.class)))
                .thenReturn(Collections.emptyList());
        lenient().when(channelProductVariantRepository.findActiveByVariantIdInWithChannel(anyList()))
                .thenReturn(Collections.emptyList());
        lenient().when(channelProductVariantRepository.findActiveByNormalizedExternalSkuInWithVariant(anyList()))
                .thenReturn(Collections.emptyList());

        // Default stubs for repositories used inside the happy paths
        lenient().when(purchaseOrderRepository.findByIdWithDetails(purchaseOrderId)).thenReturn(Optional.of(purchaseOrder));
        lenient().when(stockReceiveRepository.existsByPurchaseOrderId(purchaseOrderId)).thenReturn(false);
        lenient().when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(supplier));
        lenient().when(variantRepository.findById(variantId)).thenReturn(Optional.of(variant));
        lenient().when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        lenient().when(stockReceiveRepository.save(any(InventoryReceipt.class))).thenReturn(receipt);
        lenient().when(stockReceiveItemRepository.save(any(InventoryReceiptItem.class))).thenReturn(receiptItem);
        lenient().when(inventoryTransactionRepository.save(any(InventoryTransaction.class)))
                .thenAnswer(inv -> new InventoryTransaction());
        lenient().when(stockReceiveRepository.findTopByReceiptCodeStartingWithOrderByReceiptCodeDesc(anyString()))
                .thenReturn(Optional.empty());
        lenient().when(inventoryItemRepository.findByWarehouseIdAndVariantId(warehouseId, variantId))
                .thenReturn(Optional.of(inventoryItem));
        lenient().when(inventoryItemRepository.findByWarehouseIdAndVariantIdWithLock(warehouseId, variantId))
                .thenReturn(Optional.of(inventoryItem));
        lenient().when(inventoryItemRepository.save(any(InventoryItem.class))).thenReturn(inventoryItem);
        lenient().when(variantRepository.save(any(ProductVariant.class))).thenReturn(variant);
        lenient().when(receiptMapper.toResponse(any(InventoryReceipt.class))).thenReturn(response);
        lenient().when(receiptMapper.toItemResponse(any(InventoryReceiptItem.class))).thenReturn(itemResponse);
        lenient().when(marketplaceWarehouseConsistencyService.resolveMasterWarehouse()).thenReturn(warehouse);
    }

    @Nested
    @DisplayName("createReceipt Tests")
    class CreateReceiptTests {

        @Test
        @DisplayName("Should create CONFIRMED receipt successfully")
        void shouldCreateConfirmedReceiptSuccessfully() {
            StockReceiveResponse result = stockReceiveService.createReceipt(request, userId);

            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo("CONFIRMED");
            verify(purchaseOrderRepository).findByIdWithDetails(purchaseOrderId);
            // variantRepository.findById may be called multiple times (PO validation + line resolution)
            verify(variantRepository, atLeastOnce()).findById(variantId);
        }

        @Test
        @DisplayName("Should create DRAFT receipt successfully")
        void shouldCreateDraftReceiptSuccessfully() {
            request.setIsDraft(true);
            request.setInvoiceNumber(null);

            StockReceiveResponse draftResponse = StockReceiveResponse.builder()
                    .id(receiptId)
                    .warehouseId(warehouseId)
                    .warehouseName("Main Warehouse")
                    .supplierId(supplierId)
                    .supplierName("Test Supplier")
                    .receiptCode("PN-2026-001")
                    .invoiceNumber("PN-2026-001")
                    .status("DRAFT")
                    .totalCost(new BigDecimal("600.00"))
                    .items(List.of(itemResponse))
                    .totalSkuCount(1)
                    .totalQuantity(10)
                    .build();
            when(receiptMapper.toResponse(any(InventoryReceipt.class))).thenReturn(draftResponse);

            StockReceiveResponse result = stockReceiveService.createReceipt(request, userId);

            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo("DRAFT");
        }

        @Test
        @DisplayName("Should throw exception when purchase order not found")
        void shouldThrowExceptionWhenPurchaseOrderNotFound() {
            when(purchaseOrderRepository.findByIdWithDetails(purchaseOrderId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> stockReceiveService.createReceipt(request, userId))
                    .isInstanceOf(AppException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        }

        @Test
        @DisplayName("Should throw exception when purchase order is not in RECEIVING status")
        void shouldThrowExceptionWhenPurchaseOrderStatusInvalid() {
            purchaseOrder.setStatus(PurchaseOrderStatus.DRAFT);

            assertThatThrownBy(() -> stockReceiveService.createReceipt(request, userId))
                    .isInstanceOf(AppException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.ORDER_STATUS_INVALID_TRANSITION);
        }

        @Test
        @DisplayName("Should throw exception when items list is empty")
        void shouldThrowExceptionWhenItemsEmpty() {
            request.setItems(Collections.emptyList());

            assertThatThrownBy(() -> stockReceiveService.createReceipt(request, userId))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("at least one item");
        }

        @Test
        @DisplayName("Should throw exception when confirming receipt without quantity")
        void shouldThrowExceptionWhenConfirmingWithoutQuantity() {
            itemRequest.setQuantity(null);

            assertThatThrownBy(() -> stockReceiveService.createReceipt(request, userId))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("số lượng");
        }

        @Test
        @DisplayName("Should throw exception when confirming receipt without unit cost")
        void shouldThrowExceptionWhenConfirmingWithoutUnitCost() {
            itemRequest.setUnitCost(null);

            assertThatThrownBy(() -> stockReceiveService.createReceipt(request, userId))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("đơn giá");
        }

        @Test
        @DisplayName("Should throw exception when PO already has a receipt")
        void shouldThrowExceptionWhenDuplicateForPurchaseOrder() {
            when(stockReceiveRepository.existsByPurchaseOrderId(purchaseOrderId)).thenReturn(true);

            assertThatThrownBy(() -> stockReceiveService.createReceipt(request, userId))
                    .isInstanceOf(AppException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.CONFLICT);
        }

        @Test
        @DisplayName("Should create receipt without supplier when supplierId is null")
        void shouldCreateReceiptWithoutSupplier() {
            request.setSupplierId(null);

            StockReceiveResponse result = stockReceiveService.createReceipt(request, userId);

            assertThat(result).isNotNull();
            verify(supplierRepository, never()).findById(any());
        }

        @Test
        @DisplayName("Should throw exception when variant not found")
        void shouldThrowExceptionWhenVariantNotFound() {
            when(variantRepository.findById(variantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> stockReceiveService.createReceipt(request, userId))
                    .isInstanceOf(AppException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.VARIANT_NOT_FOUND);
        }

        @Test
        @DisplayName("Should throw exception when variant is inactive")
        void shouldThrowExceptionWhenVariantIsInactive() {
            variant.setIsActive(false);

            assertThatThrownBy(() -> stockReceiveService.createReceipt(request, userId))
                    .isInstanceOf(AppException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.VARIANT_NOT_FOUND);
        }

        @Test
        @DisplayName("Should calculate correct weighted average cost")
        void shouldCalculateCorrectAverageCost() {
            inventoryItem.setQuantityOnHand(100);
            inventoryItem.setAverageCost(new BigDecimal("50.00"));

            stockReceiveService.createReceipt(request, userId);

            ArgumentCaptor<InventoryItem> itemCaptor = ArgumentCaptor.forClass(InventoryItem.class);
            verify(inventoryItemRepository, atLeastOnce()).save(itemCaptor.capture());

            List<InventoryItem> savedItems = itemCaptor.getAllValues();
            InventoryItem lastSavedItem = savedItems.get(savedItems.size() - 1);

            // (100 * 50 + 10 * 60) / (100 + 10) = 50.91
            assertThat(lastSavedItem.getAverageCost()).isEqualByComparingTo(new BigDecimal("50.91"));
            assertThat(lastSavedItem.getQuantityOnHand()).isEqualTo(110);
        }
    }

    @Nested
    @DisplayName("getReceipts Tests")
    class GetReceiptsTests {

        @Test
        @DisplayName("Should get paginated receipts successfully")
        void shouldGetPaginatedReceiptsSuccessfully() {
            List<InventoryReceipt> receipts = List.of(receipt);
            Page<InventoryReceipt> receiptsPage = new PageImpl<>(receipts, PageRequest.of(0, 10), 1);

            when(stockReceiveRepository.findAllByOrderByCreatedAtDesc(any(PageRequest.class)))
                    .thenReturn(receiptsPage);
            when(stockReceiveItemRepository.findByReceiptId(receiptId))
                    .thenReturn(List.of(receiptItem));

            PageResponse<StockReceiveResponse> result = stockReceiveService.getReceipts(0, 10);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getPage()).isEqualTo(0);
            assertThat(result.getSize()).isEqualTo(10);
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getTotalPages()).isEqualTo(1);
            assertThat(result.isFirst()).isTrue();
            assertThat(result.isLast()).isTrue();

            StockReceiveResponse firstReceipt = result.getContent().get(0);
            assertThat(firstReceipt.getTotalSkuCount()).isEqualTo(1);
            assertThat(firstReceipt.getTotalQuantity()).isEqualTo(10);
        }

        @Test
        @DisplayName("Should return empty page when no receipts found")
        void shouldReturnEmptyPageWhenNoReceiptsFound() {
            Page<InventoryReceipt> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 10), 0);

            when(stockReceiveRepository.findAllByOrderByCreatedAtDesc(any(PageRequest.class)))
                    .thenReturn(emptyPage);

            PageResponse<StockReceiveResponse> result = stockReceiveService.getReceipts(0, 10);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("getReceiptById Tests")
    class GetReceiptByIdTests {

        @Test
        @DisplayName("Should get receipt by id successfully")
        void shouldGetReceiptByIdSuccessfully() {
            when(stockReceiveRepository.findById(receiptId)).thenReturn(Optional.of(receipt));
            when(stockReceiveItemRepository.findByReceiptId(receiptId))
                    .thenReturn(List.of(receiptItem));

            StockReceiveResponse result = stockReceiveService.getReceiptById(receiptId);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(receiptId);
            assertThat(result.getItems()).hasSize(1);
            assertThat(result.getTotalSkuCount()).isEqualTo(1);
            assertThat(result.getTotalQuantity()).isEqualTo(10);
        }

        @Test
        @DisplayName("Should throw exception when receipt not found")
        void shouldThrowExceptionWhenReceiptNotFound() {
            when(stockReceiveRepository.findById(receiptId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> stockReceiveService.getReceiptById(receiptId))
                    .isInstanceOf(AppException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.RECEIPT_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("getNextReceiptCode Tests")
    class GetNextReceiptCodeTests {

        @Test
        @DisplayName("Should return PN-YYYY-001 when no previous receipts exist")
        void shouldReturnInitialCode() {
            when(stockReceiveRepository.findTopByReceiptCodeStartingWithOrderByReceiptCodeDesc(anyString()))
                    .thenReturn(Optional.empty());

            String code = stockReceiveService.getNextReceiptCode();

            int year = LocalDate.now().getYear();
            assertThat(code).isEqualTo("PN-" + year + "-001");
        }

        @Test
        @DisplayName("Should increment suffix when previous code exists")
        void shouldIncrementSuffix() {
            InventoryReceipt latest = InventoryReceipt.builder()
                    .receiptCode("PN-2026-007")
                    .build();
            when(stockReceiveRepository.findTopByReceiptCodeStartingWithOrderByReceiptCodeDesc("PN-2026-"))
                    .thenReturn(Optional.of(latest));

            String code = stockReceiveService.getNextReceiptCode();

            assertThat(code).isEqualTo("PN-2026-008");
        }
    }

    @Nested
    @DisplayName("completeReceipt Tests")
    class CompleteReceiptTests {

        @Test
        @DisplayName("Should complete DRAFT receipt successfully")
        void shouldCompleteDraftReceiptSuccessfully() {
            receipt.setStatus("DRAFT");

            when(stockReceiveRepository.findById(receiptId)).thenReturn(Optional.of(receipt));
            when(stockReceiveItemRepository.findByReceiptId(receiptId)).thenReturn(List.of(receiptItem));

            StockReceiveResponse result = stockReceiveService.completeReceipt(receiptId, userId);

            assertThat(result).isNotNull();

            ArgumentCaptor<InventoryReceipt> receiptCaptor = ArgumentCaptor.forClass(InventoryReceipt.class);
            verify(stockReceiveRepository).save(receiptCaptor.capture());
            InventoryReceipt savedReceipt = receiptCaptor.getValue();
            assertThat(savedReceipt.getStatus()).isEqualTo("CONFIRMED");
            assertThat(savedReceipt.getConfirmedAt()).isNotNull();
            assertThat(savedReceipt.getApprovedBy()).isEqualTo(user);

            verify(inventoryItemRepository).save(any(InventoryItem.class));
            verify(inventoryTransactionRepository).save(any(InventoryTransaction.class));
        }

        @Test
        @DisplayName("Should throw exception when completing non-DRAFT receipt")
        void shouldThrowExceptionWhenCompletingNonDraftReceipt() {
            receipt.setStatus("CONFIRMED");
            when(stockReceiveRepository.findById(receiptId)).thenReturn(Optional.of(receipt));

            assertThatThrownBy(() -> stockReceiveService.completeReceipt(receiptId, userId))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("Lưu tạm");
        }

        @Test
        @DisplayName("Should throw exception when completing receipt with empty items")
        void shouldThrowExceptionWhenCompletingWithEmptyItems() {
            receipt.setStatus("DRAFT");
            when(stockReceiveRepository.findById(receiptId)).thenReturn(Optional.of(receipt));
            when(stockReceiveItemRepository.findByReceiptId(receiptId)).thenReturn(Collections.emptyList());

            assertThatThrownBy(() -> stockReceiveService.completeReceipt(receiptId, userId))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("không có sản phẩm");
        }

        @Test
        @DisplayName("Should throw exception when completing receipt with zero quantity")
        void shouldThrowExceptionWhenItemHasZeroQuantity() {
            receipt.setStatus("DRAFT");
            receiptItem.setQuantity(0);
            when(stockReceiveRepository.findById(receiptId)).thenReturn(Optional.of(receipt));
            when(stockReceiveItemRepository.findByReceiptId(receiptId)).thenReturn(List.of(receiptItem));

            assertThatThrownBy(() -> stockReceiveService.completeReceipt(receiptId, userId))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("số lượng");
        }

        @Test
        @DisplayName("Should create new inventory item when completing receipt and item doesn't exist")
        void shouldCreateNewInventoryItemWhenCompleting() {
            receipt.setStatus("DRAFT");
            when(stockReceiveRepository.findById(receiptId)).thenReturn(Optional.of(receipt));
            when(stockReceiveItemRepository.findByReceiptId(receiptId)).thenReturn(List.of(receiptItem));

            InventoryItem newInventoryItem = InventoryItem.builder()
                    .id(UUID.randomUUID())
                    .warehouse(warehouse)
                    .variant(variant)
                    .quantityOnHand(0)
                    .averageCost(BigDecimal.ZERO)
                    .build();
            when(inventoryItemRepository.findByWarehouseIdAndVariantId(warehouseId, variantId))
                    .thenReturn(Optional.empty());
            when(inventoryItemRepository.save(any(InventoryItem.class))).thenReturn(newInventoryItem);

            StockReceiveResponse result = stockReceiveService.completeReceipt(receiptId, userId);

            assertThat(result).isNotNull();
            verify(inventoryItemRepository, atLeastOnce()).save(any(InventoryItem.class));
        }

        @Test
        @DisplayName("Should create CONFIRMED IMPORT transaction when completing receipt")
        void shouldCreateConfirmedTransactionWhenCompleting() {
            receipt.setStatus("DRAFT");
            when(stockReceiveRepository.findById(receiptId)).thenReturn(Optional.of(receipt));
            when(stockReceiveItemRepository.findByReceiptId(receiptId)).thenReturn(List.of(receiptItem));

            stockReceiveService.completeReceipt(receiptId, userId);

            ArgumentCaptor<InventoryTransaction> transactionCaptor =
                    ArgumentCaptor.forClass(InventoryTransaction.class);
            verify(inventoryTransactionRepository, atLeastOnce()).save(transactionCaptor.capture());

            // We need at least one IMPORT / RECEIPT transaction
            boolean foundCompleted = transactionCaptor.getAllValues().stream()
                    .anyMatch(t -> t.getType() == InvTxnType.IMPORT
                            && "RECEIPT".equals(t.getReferenceType())
                            && receiptId.equals(t.getReferenceId())
                            && t.getPerformedBy() == user);
            assertThat(foundCompleted).isTrue();
        }
    }

    @Nested
    @DisplayName("statistics & sync Tests")
    class StatsAndSyncTests {

        @Test
        @DisplayName("getReceiptStatistics should aggregate counts by status")
        void getReceiptStatistics_aggregates() {
            when(stockReceiveRepository.count()).thenReturn(10L);
            when(stockReceiveRepository.countByStatus("CONFIRMED")).thenReturn(7L);
            when(stockReceiveRepository.countByStatus("DRAFT")).thenReturn(2L);
            when(stockReceiveRepository.countByStatus("CANCELLED")).thenReturn(1L);

            Object stats = stockReceiveService.getReceiptStatistics();

            assertThat(stats).isInstanceOf(Map.class);
            Map<?, ?> map = (Map<?, ?>) stats;
            assertThat(map.get("totalCount")).isEqualTo(10L);
            assertThat(map.get("confirmedCount")).isEqualTo(7L);
            assertThat(map.get("draftCount")).isEqualTo(2L);
            assertThat(map.get("cancelledCount")).isEqualTo(1L);
        }

        @Test
        @DisplayName("syncPendingMarketplaceInventory returns 0 when no pending variants")
        void syncPendingMarketplaceInventory_empty() {
            when(stockReceiveRepository.findConfirmedVariantIdsPendingMarketplaceSync())
                    .thenReturn(Collections.emptyList());

            int pushed = stockReceiveService.syncPendingMarketplaceInventory();

            assertThat(pushed).isZero();
            verify(marketplaceInventoryPropagationService, never()).pushAvailableStock(any());
        }
    }
}