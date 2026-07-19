package fu.osms.inventory.service.impl;

import fu.osms.auth.entity.User;
import fu.osms.auth.repository.UserRepository;
import fu.osms.catalog.entity.Product;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.catalog.repository.ProductVariantRepository;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.inventory.dto.request.StockReceiveItemRequest;
import fu.osms.inventory.dto.request.StockReceiveRequest;
import fu.osms.inventory.dto.response.StockReceiveItemResponse;
import fu.osms.inventory.dto.response.StockReceiveResponse;
import fu.osms.common.dto.PageResponse;
import fu.osms.inventory.entity.*;
import fu.osms.inventory.enums.InvTxnType;
import fu.osms.inventory.mapper.StockReceiveMapper;
import fu.osms.inventory.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
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

    @InjectMocks
    private StockReceiveServiceImpl stockReceiveService;

    // Test data
    private UUID warehouseId;
    private UUID supplierId;
    private UUID variantId;
    private UUID userId;
    private UUID receiptId;

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

    @BeforeEach
    void setUp() {
        // Initialize test UUIDs
        warehouseId = UUID.randomUUID();
        supplierId = UUID.randomUUID();
        variantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        receiptId = UUID.randomUUID();

        // Setup warehouse
        warehouse = Warehouse.builder()
                .id(warehouseId)
                .name("Main Warehouse")
                .isActive(true)
                .build();

        // Setup supplier
        supplier = Supplier.builder()
                .id(supplierId)
                .name("Test Supplier")
                .build();

        // Setup product
        product = Product.builder()
                .id(UUID.randomUUID())
                .name("Test Product")
                .build();

        // Setup variant
        variant = ProductVariant.builder()
                .id(variantId)
                .product(product)
                .sku("SKU001")
                .name("Test Variant")
                .isActive(true)
                .deletedAt(null)
                .build();

        // Setup user
        user = User.builder()
                .id(userId)
                .email("testuser@example.com")
                .fullName("Test User")
                .build();

        // Setup inventory item
        inventoryItem = InventoryItem.builder()
                .id(UUID.randomUUID())
                .warehouse(warehouse)
                .variant(variant)
                .quantityOnHand(100)
                .averageCost(new BigDecimal("50.00"))
                .build();

        // Setup receipt item request
        itemRequest = StockReceiveItemRequest.builder()
                .variantId(variantId)
                .quantity(10)
                .unitCost(new BigDecimal("60.00"))
                .notes("Test item")
                .build();

        // Setup receipt request - isDraft=false means CONFIRMED immediately
        request = StockReceiveRequest.builder()
                .warehouseId(warehouseId)
                .supplierId(supplierId)
                .invoiceNumber("INV001")
                .receivedAt(LocalDate.now())
                .notes("Test receipt")
                .items(List.of(itemRequest))
                .isDraft(false)
                .build();

        // Setup receipt entity
        receipt = InventoryReceipt.builder()
                .id(receiptId)
                .warehouse(warehouse)
                .supplier(supplier)
                .receiptCode("PN-2026-001")
                .invoiceNumber("PN-2026-001")  // Note: invoiceNumber is set to receiptCode
                .status("CONFIRMED")
                .totalCost(new BigDecimal("600.00"))
                .receivedAt(OffsetDateTime.now())
                .notes("Test receipt")
                .createdBy(user)
                .build();

        // Setup receipt item entity
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

        // Setup response DTOs
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
    }

    @Nested
    @DisplayName("createReceipt Tests")
    class CreateReceiptTests {

        @Test
        @DisplayName("Should create CONFIRMED receipt successfully")
        void shouldCreateConfirmedReceiptSuccessfully() {
            // Arrange
            when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
            when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(supplier));
            // Note: NO existsByInvoiceNumber call for CONFIRMED receipt
            when(variantRepository.findById(variantId)).thenReturn(Optional.of(variant));
            when(stockReceiveRepository.findTopByReceiptCodeStartingWithOrderByReceiptCodeDesc(anyString())).thenReturn(Optional.empty());
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(stockReceiveRepository.save(any(InventoryReceipt.class))).thenReturn(receipt);
            when(inventoryItemRepository.findByWarehouseIdAndVariantId(warehouseId, variantId))
                    .thenReturn(Optional.of(inventoryItem));
            when(inventoryItemRepository.findByWarehouseIdAndVariantIdWithLock(warehouseId, variantId))
                    .thenReturn(Optional.of(inventoryItem));
            when(inventoryItemRepository.save(any(InventoryItem.class))).thenReturn(inventoryItem);
            when(stockReceiveItemRepository.save(any(InventoryReceiptItem.class))).thenReturn(receiptItem);
            when(inventoryTransactionRepository.save(any(InventoryTransaction.class)))
                    .thenReturn(new InventoryTransaction());
            when(receiptMapper.toResponse(any(InventoryReceipt.class))).thenReturn(response);
            when(receiptMapper.toItemResponse(any(InventoryReceiptItem.class))).thenReturn(itemResponse);

            // Act
            StockReceiveResponse result = stockReceiveService.createReceipt(request, userId);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo("CONFIRMED");
            assertThat(result.getTotalCost()).isEqualTo(new BigDecimal("600.00"));
            assertThat(result.getTotalSkuCount()).isEqualTo(1);
            assertThat(result.getTotalQuantity()).isEqualTo(10);

            // Verify repository calls
            verify(warehouseRepository).findById(warehouseId);
            verify(supplierRepository).findById(supplierId);
            // NO existsByInvoiceNumber call for CONFIRMED receipt
            verify(stockReceiveRepository, never()).existsByInvoiceNumber(any());
            verify(variantRepository).findById(variantId);
            verify(stockReceiveRepository).save(any(InventoryReceipt.class));
            verify(inventoryItemRepository).save(any(InventoryItem.class));
            verify(stockReceiveItemRepository).save(any(InventoryReceiptItem.class));
            verify(inventoryTransactionRepository).save(any(InventoryTransaction.class));
        }

        @Test
        @DisplayName("Should create DRAFT receipt successfully")
        void shouldCreateDraftReceiptSuccessfully() {
            // Arrange
            request.setIsDraft(true);
            request.setInvoiceNumber(null);  // Invoice number not required for draft
            itemRequest.setQuantity(5);      // DRAFT still requires quantity > 0 per current validation
            itemRequest.setUnitCost(BigDecimal.ZERO);

            receipt.setStatus("DRAFT");
            receipt.setConfirmedAt(null);
            receipt.setInvoiceNumber("PN-2026-001");

            when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
            when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(supplier));
            when(variantRepository.findById(variantId)).thenReturn(Optional.of(variant));
            when(stockReceiveRepository.findTopByReceiptCodeStartingWithOrderByReceiptCodeDesc(anyString())).thenReturn(Optional.empty());
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(stockReceiveRepository.save(any(InventoryReceipt.class))).thenReturn(receipt);
            when(stockReceiveItemRepository.save(any(InventoryReceiptItem.class))).thenReturn(receiptItem);
            when(inventoryTransactionRepository.save(any(InventoryTransaction.class)))
                    .thenReturn(new InventoryTransaction());

            response.setStatus("DRAFT");
            when(receiptMapper.toResponse(any(InventoryReceipt.class))).thenReturn(response);
            when(receiptMapper.toItemResponse(any(InventoryReceiptItem.class))).thenReturn(itemResponse);

            // Act
            StockReceiveResponse result = stockReceiveService.createReceipt(request, userId);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo("DRAFT");

            // Verify inventory NOT updated for DRAFT
            verify(inventoryItemRepository, never()).findByWarehouseIdAndVariantIdWithLock(any(), any());
            verify(stockReceiveItemRepository).save(any(InventoryReceiptItem.class));
            verify(inventoryTransactionRepository).save(any(InventoryTransaction.class));
        }

        @Test
        @DisplayName("Should throw exception when warehouse not found")
        void shouldThrowExceptionWhenWarehouseNotFound() {
            // Arrange
            when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> stockReceiveService.createReceipt(request, userId))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.WAREHOUSE_NOT_FOUND);

            verify(warehouseRepository).findById(warehouseId);
            verifyNoInteractions(stockReceiveRepository);
        }

        @Test
        @DisplayName("Should throw exception when warehouse is inactive")
        void shouldThrowExceptionWhenWarehouseIsInactive() {
            // Arrange
            warehouse.setIsActive(false);
            when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));

            // Act & Assert
            assertThatThrownBy(() -> stockReceiveService.createReceipt(request, userId))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.WAREHOUSE_NOT_FOUND);
        }

        @Test
        @DisplayName("Should throw exception when supplier not found")
        void shouldThrowExceptionWhenSupplierNotFound() {
            // Arrange
            when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
            when(supplierRepository.findById(supplierId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> stockReceiveService.createReceipt(request, userId))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SUPPLIER_NOT_FOUND);
        }

        @Test
        @DisplayName("Should throw exception when items list is empty")
        void shouldThrowExceptionWhenItemsEmpty() {
            // Arrange
            request.setItems(Collections.emptyList());
            when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
            when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(supplier));

            // Act & Assert
            assertThatThrownBy(() -> stockReceiveService.createReceipt(request, userId))
                    .isInstanceOf(AppException.class)
                    .extracting("errorCode", "message")
                    .containsExactly(ErrorCode.VALIDATION_FAILED, "Receipt must have at least one item");
        }

        @Test
        @DisplayName("Should throw exception when confirming receipt without quantity")
        void shouldThrowExceptionWhenConfirmingWithoutQuantity() {
            // Arrange
            itemRequest.setQuantity(null);
            when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
            when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(supplier));

            // Act & Assert
            assertThatThrownBy(() -> stockReceiveService.createReceipt(request, userId))
                    .isInstanceOf(AppException.class)
                    .extracting("errorCode", "message")
                    .containsExactly(ErrorCode.VALIDATION_FAILED,
                            "Tất cả sản phẩm phải có số lượng lớn hơn 0 khi xác nhận phiếu nhập");
        }

        @Test
        @DisplayName("Should throw exception when confirming receipt without unit cost")
        void shouldThrowExceptionWhenConfirmingWithoutUnitCost() {
            // Arrange
            itemRequest.setUnitCost(null);
            when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
            when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(supplier));

            // Act & Assert
            assertThatThrownBy(() -> stockReceiveService.createReceipt(request, userId))
                    .isInstanceOf(AppException.class)
                    .extracting("errorCode", "message")
                    .containsExactly(ErrorCode.VALIDATION_FAILED,
                            "Tất cả sản phẩm phải có đơn giá lớn hơn hoặc bằng 0 khi xác nhận phiếu nhập");
        }

        @Test
        @DisplayName("Should throw exception when duplicate variants in request")
        void shouldThrowExceptionWhenDuplicateVariants() {
            // Arrange
            StockReceiveItemRequest duplicateItem = StockReceiveItemRequest.builder()
                    .variantId(variantId)  // Same variant ID
                    .quantity(5)
                    .unitCost(new BigDecimal("70.00"))
                    .build();
            request.setItems(List.of(itemRequest, duplicateItem));

            when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
            when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(supplier));

            // Act & Assert
            assertThatThrownBy(() -> stockReceiveService.createReceipt(request, userId))
                    .isInstanceOf(AppException.class)
                    .extracting("errorCode", "message")
                    .containsExactly(ErrorCode.VALIDATION_FAILED, "Duplicate variant in receipt items");
        }

        @Test
        @DisplayName("Should throw exception when variant not found")
        void shouldThrowExceptionWhenVariantNotFound() {
            // Arrange
            when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
            when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(supplier));
            when(variantRepository.findById(variantId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> stockReceiveService.createReceipt(request, userId))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VARIANT_NOT_FOUND);
        }

        @Test
        @DisplayName("Should throw exception when variant is inactive")
        void shouldThrowExceptionWhenVariantIsInactive() {
            // Arrange
            variant.setIsActive(false);
            when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
            when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(supplier));
            when(variantRepository.findById(variantId)).thenReturn(Optional.of(variant));

            // Act & Assert
            assertThatThrownBy(() -> stockReceiveService.createReceipt(request, userId))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VARIANT_NOT_FOUND);
        }

        @Test
        @DisplayName("Should create receipt without supplier")
        void shouldCreateReceiptWithoutSupplier() {
            // Arrange
            request.setSupplierId(null);
            when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
            when(variantRepository.findById(variantId)).thenReturn(Optional.of(variant));
            when(stockReceiveRepository.findTopByReceiptCodeStartingWithOrderByReceiptCodeDesc(anyString())).thenReturn(Optional.empty());
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(stockReceiveRepository.save(any(InventoryReceipt.class))).thenReturn(receipt);
            when(inventoryItemRepository.findByWarehouseIdAndVariantId(warehouseId, variantId))
                    .thenReturn(Optional.of(inventoryItem));
            when(inventoryItemRepository.findByWarehouseIdAndVariantIdWithLock(warehouseId, variantId))
                    .thenReturn(Optional.of(inventoryItem));
            when(inventoryItemRepository.save(any(InventoryItem.class))).thenReturn(inventoryItem);
            when(stockReceiveItemRepository.save(any(InventoryReceiptItem.class))).thenReturn(receiptItem);
            when(inventoryTransactionRepository.save(any(InventoryTransaction.class)))
                    .thenReturn(new InventoryTransaction());
            when(receiptMapper.toResponse(any(InventoryReceipt.class))).thenReturn(response);
            when(receiptMapper.toItemResponse(any(InventoryReceiptItem.class))).thenReturn(itemResponse);

            // Act
            StockReceiveResponse result = stockReceiveService.createReceipt(request, userId);

            // Assert
            assertThat(result).isNotNull();
            verify(supplierRepository, never()).findById(any());
        }

        @Test
        @DisplayName("Should create inventory item when it doesn't exist")
        void shouldCreateInventoryItemWhenNotExists() {
            // Arrange
            when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
            when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(supplier));
            when(variantRepository.findById(variantId)).thenReturn(Optional.of(variant));
            when(stockReceiveRepository.findTopByReceiptCodeStartingWithOrderByReceiptCodeDesc(anyString())).thenReturn(Optional.empty());
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(stockReceiveRepository.save(any(InventoryReceipt.class))).thenReturn(receipt);

            // First call returns empty (item doesn't exist), second call returns the created item
            when(inventoryItemRepository.findByWarehouseIdAndVariantId(warehouseId, variantId))
                    .thenReturn(Optional.empty());

            InventoryItem newInventoryItem = InventoryItem.builder()
                    .id(UUID.randomUUID())
                    .warehouse(warehouse)
                    .variant(variant)
                    .quantityOnHand(0)
                    .averageCost(BigDecimal.ZERO)
                    .build();

            when(inventoryItemRepository.save(any(InventoryItem.class))).thenReturn(newInventoryItem);
            when(inventoryItemRepository.findByWarehouseIdAndVariantIdWithLock(warehouseId, variantId))
                    .thenReturn(Optional.of(newInventoryItem));
            when(stockReceiveItemRepository.save(any(InventoryReceiptItem.class))).thenReturn(receiptItem);
            when(inventoryTransactionRepository.save(any(InventoryTransaction.class)))
                    .thenReturn(new InventoryTransaction());
            when(receiptMapper.toResponse(any(InventoryReceipt.class))).thenReturn(response);
            when(receiptMapper.toItemResponse(any(InventoryReceiptItem.class))).thenReturn(itemResponse);

            // Act
            StockReceiveResponse result = stockReceiveService.createReceipt(request, userId);

            // Assert
            assertThat(result).isNotNull();

            // Verify inventory item was created (saved twice - once for creation, once for update)
            verify(inventoryItemRepository, times(2)).save(any(InventoryItem.class));
        }

        @Test
        @DisplayName("Should calculate correct average cost")
        void shouldCalculateCorrectAverageCost() {
            // Arrange
            inventoryItem.setQuantityOnHand(100);
            inventoryItem.setAverageCost(new BigDecimal("50.00"));

            when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
            when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(supplier));
            when(variantRepository.findById(variantId)).thenReturn(Optional.of(variant));
            when(stockReceiveRepository.findTopByReceiptCodeStartingWithOrderByReceiptCodeDesc(anyString())).thenReturn(Optional.empty());
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(stockReceiveRepository.save(any(InventoryReceipt.class))).thenReturn(receipt);
            when(inventoryItemRepository.findByWarehouseIdAndVariantId(warehouseId, variantId))
                    .thenReturn(Optional.of(inventoryItem));
            when(inventoryItemRepository.findByWarehouseIdAndVariantIdWithLock(warehouseId, variantId))
                    .thenReturn(Optional.of(inventoryItem));
            when(inventoryItemRepository.save(any(InventoryItem.class))).thenReturn(inventoryItem);
            when(stockReceiveItemRepository.save(any(InventoryReceiptItem.class))).thenReturn(receiptItem);
            when(inventoryTransactionRepository.save(any(InventoryTransaction.class)))
                    .thenReturn(new InventoryTransaction());
            when(receiptMapper.toResponse(any(InventoryReceipt.class))).thenReturn(response);
            when(receiptMapper.toItemResponse(any(InventoryReceiptItem.class))).thenReturn(itemResponse);

            // Act
            stockReceiveService.createReceipt(request, userId);

            // Assert - Capture the saved inventory item to verify average cost calculation
            ArgumentCaptor<InventoryItem> itemCaptor = ArgumentCaptor.forClass(InventoryItem.class);
            verify(inventoryItemRepository).save(itemCaptor.capture());

            InventoryItem savedItem = itemCaptor.getValue();
            // Expected: (100 * 50.00 + 10 * 60.00) / (100 + 10) = 50.91
            assertThat(savedItem.getAverageCost()).isEqualByComparingTo(new BigDecimal("50.91"));
            assertThat(savedItem.getQuantityOnHand()).isEqualTo(110);
        }
    }

    @Nested
    @DisplayName("getReceipts Tests")
    class GetReceiptsTests {

        @Test
        @DisplayName("Should get paginated receipts successfully")
        void shouldGetPaginatedReceiptsSuccessfully() {
            // Arrange
            List<InventoryReceipt> receipts = List.of(receipt);
            Page<InventoryReceipt> receiptsPage = new PageImpl<>(receipts, PageRequest.of(0, 10), 1);

            when(stockReceiveRepository.findAllByOrderByCreatedAtDesc(any(PageRequest.class)))
                    .thenReturn(receiptsPage);
            when(stockReceiveItemRepository.findByReceiptId(receiptId))
                    .thenReturn(List.of(receiptItem));
            when(receiptMapper.toResponse(any(InventoryReceipt.class))).thenReturn(response);
            when(receiptMapper.toItemResponse(any(InventoryReceiptItem.class))).thenReturn(itemResponse);

            // Act
            PageResponse<StockReceiveResponse> result = stockReceiveService.getReceipts(0, 10);

            // Assert
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

            verify(stockReceiveRepository).findAllByOrderByCreatedAtDesc(any(PageRequest.class));
            verify(stockReceiveItemRepository).findByReceiptId(receiptId);
        }

        @Test
        @DisplayName("Should return empty page when no receipts found")
        void shouldReturnEmptyPageWhenNoReceiptsFound() {
            // Arrange
            Page<InventoryReceipt> emptyPage = new PageImpl<>(Collections.emptyList(),
                    PageRequest.of(0, 10), 0);

            when(stockReceiveRepository.findAllByOrderByCreatedAtDesc(any(PageRequest.class)))
                    .thenReturn(emptyPage);

            // Act
            PageResponse<StockReceiveResponse> result = stockReceiveService.getReceipts(0, 10);

            // Assert
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
            // Arrange
            when(stockReceiveRepository.findById(receiptId)).thenReturn(Optional.of(receipt));
            when(stockReceiveItemRepository.findByReceiptId(receiptId))
                    .thenReturn(List.of(receiptItem));
            when(receiptMapper.toResponse(any(InventoryReceipt.class))).thenReturn(response);
            when(receiptMapper.toItemResponse(any(InventoryReceiptItem.class))).thenReturn(itemResponse);

            // Act
            StockReceiveResponse result = stockReceiveService.getReceiptById(receiptId);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(receiptId);
            assertThat(result.getItems()).hasSize(1);
            assertThat(result.getTotalSkuCount()).isEqualTo(1);
            assertThat(result.getTotalQuantity()).isEqualTo(10);

            verify(stockReceiveRepository).findById(receiptId);
            verify(stockReceiveItemRepository).findByReceiptId(receiptId);
        }

        @Test
        @DisplayName("Should throw exception when receipt not found")
        void shouldThrowExceptionWhenReceiptNotFound() {
            // Arrange
            when(stockReceiveRepository.findById(receiptId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> stockReceiveService.getReceiptById(receiptId))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RECEIPT_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("updateReceipt Tests")
    class UpdateReceiptTests {

        @Test
        @DisplayName("Should update DRAFT receipt successfully")
        void shouldUpdateDraftReceiptSuccessfully() {
            // Arrange
            receipt.setStatus("DRAFT");

            when(stockReceiveRepository.findById(receiptId)).thenReturn(Optional.of(receipt));
            when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
            when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(supplier));
            when(variantRepository.findById(variantId)).thenReturn(Optional.of(variant));
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(stockReceiveRepository.save(any(InventoryReceipt.class))).thenReturn(receipt);
            when(stockReceiveItemRepository.findByReceiptId(receiptId))
                    .thenReturn(List.of(receiptItem));
            when(stockReceiveItemRepository.save(any(InventoryReceiptItem.class))).thenReturn(receiptItem);
            when(inventoryTransactionRepository.save(any(InventoryTransaction.class)))
                    .thenReturn(new InventoryTransaction());
            when(receiptMapper.toResponse(any(InventoryReceipt.class))).thenReturn(response);
            when(receiptMapper.toItemResponse(any(InventoryReceiptItem.class))).thenReturn(itemResponse);

            request.setIsDraft(true);  // Update as draft

            // Act
            StockReceiveResponse result = stockReceiveService.updateReceipt(receiptId, request, userId);

            // Assert
            assertThat(result).isNotNull();

            verify(stockReceiveRepository).findById(receiptId);
            verify(stockReceiveRepository).save(any(InventoryReceipt.class));
            verify(stockReceiveItemRepository).deleteAll(any());
            verify(stockReceiveItemRepository).save(any(InventoryReceiptItem.class));
            verify(inventoryTransactionRepository).save(any(InventoryTransaction.class));
        }

        @Test
        @DisplayName("Should throw exception when updating non-DRAFT receipt")
        void shouldThrowExceptionWhenUpdatingNonDraftReceipt() {
            // Arrange
            receipt.setStatus("CONFIRMED");
            when(stockReceiveRepository.findById(receiptId)).thenReturn(Optional.of(receipt));

            // Act & Assert
            assertThatThrownBy(() -> stockReceiveService.updateReceipt(receiptId, request, userId))
                    .isInstanceOf(AppException.class)
                    .extracting("errorCode", "message")
                    .containsExactly(ErrorCode.VALIDATION_FAILED,
                            "Chỉ có thể chỉnh sửa phiếu nhập ở trạng thái Lưu tạm");
        }

        @Test
        @DisplayName("Should throw exception when receipt not found for update")
        void shouldThrowExceptionWhenReceiptNotFoundForUpdate() {
            // Arrange
            when(stockReceiveRepository.findById(receiptId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> stockReceiveService.updateReceipt(receiptId, request, userId))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RECEIPT_NOT_FOUND);
        }

        @Test
        @DisplayName("Should not check invoice number uniqueness for DRAFT update")
        void shouldNotCheckInvoiceNumberUniquenessForDraftUpdate() {
            // Arrange
            receipt.setStatus("DRAFT");
            request.setIsDraft(true);

            when(stockReceiveRepository.findById(receiptId)).thenReturn(Optional.of(receipt));
            when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
            when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(supplier));
            when(variantRepository.findById(variantId)).thenReturn(Optional.of(variant));
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(stockReceiveRepository.save(any(InventoryReceipt.class))).thenReturn(receipt);
            when(stockReceiveItemRepository.findByReceiptId(receiptId))
                    .thenReturn(List.of(receiptItem));
            when(stockReceiveItemRepository.save(any(InventoryReceiptItem.class))).thenReturn(receiptItem);
            when(inventoryTransactionRepository.save(any(InventoryTransaction.class)))
                    .thenReturn(new InventoryTransaction());
            when(receiptMapper.toResponse(any(InventoryReceipt.class))).thenReturn(response);
            when(receiptMapper.toItemResponse(any(InventoryReceiptItem.class))).thenReturn(itemResponse);

            // Act
            StockReceiveResponse result = stockReceiveService.updateReceipt(receiptId, request, userId);

            // Assert
            assertThat(result).isNotNull();
            // NO existsByInvoiceNumber check for DRAFT update
            verify(stockReceiveRepository, never()).existsByInvoiceNumber(any());
        }
    }

    @Nested
    @DisplayName("completeReceipt Tests")
    class CompleteReceiptTests {

        @Test
        @DisplayName("Should complete DRAFT receipt successfully")
        void shouldCompleteDraftReceiptSuccessfully() {
            // Arrange
            receipt.setStatus("DRAFT");
            receipt.setInvoiceNumber("PN-2026-001");  // Note: invoiceNumber is set to receiptCode

            when(stockReceiveRepository.findById(receiptId)).thenReturn(Optional.of(receipt));
            when(stockReceiveItemRepository.findByReceiptId(receiptId))
                    .thenReturn(List.of(receiptItem));
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(inventoryItemRepository.findByWarehouseIdAndVariantId(warehouseId, variantId))
                    .thenReturn(Optional.of(inventoryItem));
            when(inventoryItemRepository.findByWarehouseIdAndVariantIdWithLock(warehouseId, variantId))
                    .thenReturn(Optional.of(inventoryItem));
            when(inventoryItemRepository.save(any(InventoryItem.class))).thenReturn(inventoryItem);
            when(stockReceiveItemRepository.save(any(InventoryReceiptItem.class))).thenReturn(receiptItem);
            when(inventoryTransactionRepository.save(any(InventoryTransaction.class)))
                    .thenReturn(new InventoryTransaction());
            when(stockReceiveRepository.save(any(InventoryReceipt.class))).thenReturn(receipt);
            when(receiptMapper.toResponse(any(InventoryReceipt.class))).thenReturn(response);
            when(receiptMapper.toItemResponse(any(InventoryReceiptItem.class))).thenReturn(itemResponse);

            // Act
            StockReceiveResponse result = stockReceiveService.completeReceipt(receiptId, userId);

            // Assert
            assertThat(result).isNotNull();

            // Verify receipt status was updated
            ArgumentCaptor<InventoryReceipt> receiptCaptor = ArgumentCaptor.forClass(InventoryReceipt.class);
            verify(stockReceiveRepository).save(receiptCaptor.capture());
            InventoryReceipt savedReceipt = receiptCaptor.getValue();
            assertThat(savedReceipt.getStatus()).isEqualTo("CONFIRMED");
            assertThat(savedReceipt.getConfirmedAt()).isNotNull();
            assertThat(savedReceipt.getApprovedBy()).isEqualTo(user);

            // Verify inventory was updated
            verify(inventoryItemRepository).save(any(InventoryItem.class));
            verify(inventoryTransactionRepository).save(any(InventoryTransaction.class));
        }

        @Test
        @DisplayName("Should throw exception when completing non-DRAFT receipt")
        void shouldThrowExceptionWhenCompletingNonDraftReceipt() {
            // Arrange
            receipt.setStatus("CONFIRMED");
            when(stockReceiveRepository.findById(receiptId)).thenReturn(Optional.of(receipt));

            // Act & Assert
            assertThatThrownBy(() -> stockReceiveService.completeReceipt(receiptId, userId))
                    .isInstanceOf(AppException.class)
                    .extracting("errorCode", "message")
                    .containsExactly(ErrorCode.VALIDATION_FAILED,
                            "Chỉ có thể hoàn thành phiếu nhập ở trạng thái Lưu tạm");
        }

        @Test
        @DisplayName("Should throw exception when completing receipt with empty items")
        void shouldThrowExceptionWhenCompletingWithEmptyItems() {
            // Arrange
            receipt.setStatus("DRAFT");
            when(stockReceiveRepository.findById(receiptId)).thenReturn(Optional.of(receipt));
            when(stockReceiveItemRepository.findByReceiptId(receiptId))
                    .thenReturn(Collections.emptyList());

            // Act & Assert
            assertThatThrownBy(() -> stockReceiveService.completeReceipt(receiptId, userId))
                    .isInstanceOf(AppException.class)
                    .extracting("errorCode", "message")
                    .containsExactly(ErrorCode.VALIDATION_FAILED, "Phiếu nhập không có sản phẩm nào");
        }

        @Test
        @DisplayName("Should throw exception when item has zero quantity")
        void shouldThrowExceptionWhenItemHasZeroQuantity() {
            // Arrange
            receipt.setStatus("DRAFT");
            receiptItem.setQuantity(0);

            when(stockReceiveRepository.findById(receiptId)).thenReturn(Optional.of(receipt));
            when(stockReceiveItemRepository.findByReceiptId(receiptId))
                    .thenReturn(List.of(receiptItem));

            // Act & Assert
            assertThatThrownBy(() -> stockReceiveService.completeReceipt(receiptId, userId))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED)
                    .hasMessageContaining("phải có số lượng lớn hơn 0");
        }

        @Test
        @DisplayName("Should throw exception when item has zero unit cost")
        void shouldThrowExceptionWhenItemHasZeroUnitCost() {
            // Arrange
            receipt.setStatus("DRAFT");
            receiptItem.setUnitCost(BigDecimal.ZERO);

            when(stockReceiveRepository.findById(receiptId)).thenReturn(Optional.of(receipt));
            when(stockReceiveItemRepository.findByReceiptId(receiptId))
                    .thenReturn(List.of(receiptItem));

            // Act & Assert
            assertThatThrownBy(() -> stockReceiveService.completeReceipt(receiptId, userId))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED)
                    .hasMessageContaining("phải có đơn giá lớn hơn 0");
        }

        @Test
        @DisplayName("Should create new inventory item when completing receipt and item doesn't exist")
        void shouldCreateNewInventoryItemWhenCompleting() {
            // Arrange
            receipt.setStatus("DRAFT");

            when(stockReceiveRepository.findById(receiptId)).thenReturn(Optional.of(receipt));
            when(stockReceiveItemRepository.findByReceiptId(receiptId))
                    .thenReturn(List.of(receiptItem));
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(inventoryItemRepository.findByWarehouseIdAndVariantId(warehouseId, variantId))
                    .thenReturn(Optional.empty());

            InventoryItem newInventoryItem = InventoryItem.builder()
                    .id(UUID.randomUUID())
                    .warehouse(warehouse)
                    .variant(variant)
                    .quantityOnHand(0)
                    .averageCost(BigDecimal.ZERO)
                    .build();

            when(inventoryItemRepository.save(any(InventoryItem.class))).thenReturn(newInventoryItem);
            when(inventoryItemRepository.findByWarehouseIdAndVariantIdWithLock(warehouseId, variantId))
                    .thenReturn(Optional.of(newInventoryItem));
            when(stockReceiveItemRepository.save(any(InventoryReceiptItem.class))).thenReturn(receiptItem);
            when(inventoryTransactionRepository.save(any(InventoryTransaction.class)))
                    .thenReturn(new InventoryTransaction());
            when(stockReceiveRepository.save(any(InventoryReceipt.class))).thenReturn(receipt);
            when(receiptMapper.toResponse(any(InventoryReceipt.class))).thenReturn(response);
            when(receiptMapper.toItemResponse(any(InventoryReceiptItem.class))).thenReturn(itemResponse);

            // Act
            StockReceiveResponse result = stockReceiveService.completeReceipt(receiptId, userId);

            // Assert
            assertThat(result).isNotNull();

            // Verify inventory item was created (saved twice - once for creation, once for update)
            verify(inventoryItemRepository, times(2)).save(any(InventoryItem.class));
        }

        @Test
        @DisplayName("Should create CONFIRMED transaction when completing receipt")
        void shouldCreateConfirmedTransactionWhenCompleting() {
            // Arrange
            receipt.setStatus("DRAFT");

            when(stockReceiveRepository.findById(receiptId)).thenReturn(Optional.of(receipt));
            when(stockReceiveItemRepository.findByReceiptId(receiptId))
                    .thenReturn(List.of(receiptItem));
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(inventoryItemRepository.findByWarehouseIdAndVariantId(warehouseId, variantId))
                    .thenReturn(Optional.of(inventoryItem));
            when(inventoryItemRepository.findByWarehouseIdAndVariantIdWithLock(warehouseId, variantId))
                    .thenReturn(Optional.of(inventoryItem));
            when(inventoryItemRepository.save(any(InventoryItem.class))).thenReturn(inventoryItem);
            when(stockReceiveItemRepository.save(any(InventoryReceiptItem.class))).thenReturn(receiptItem);
            when(inventoryTransactionRepository.save(any(InventoryTransaction.class)))
                    .thenReturn(new InventoryTransaction());
            when(stockReceiveRepository.save(any(InventoryReceipt.class))).thenReturn(receipt);
            when(receiptMapper.toResponse(any(InventoryReceipt.class))).thenReturn(response);
            when(receiptMapper.toItemResponse(any(InventoryReceiptItem.class))).thenReturn(itemResponse);

            // Act
            stockReceiveService.completeReceipt(receiptId, userId);

            // Assert - Verify transaction was created with correct note
            ArgumentCaptor<InventoryTransaction> transactionCaptor =
                    ArgumentCaptor.forClass(InventoryTransaction.class);
            verify(inventoryTransactionRepository).save(transactionCaptor.capture());

            InventoryTransaction savedTransaction = transactionCaptor.getValue();
            assertThat(savedTransaction.getType()).isEqualTo(InvTxnType.IMPORT);
            assertThat(savedTransaction.getReferenceType()).isEqualTo("RECEIPT");
            assertThat(savedTransaction.getReferenceId()).isEqualTo(receiptId);
            assertThat(savedTransaction.getNote()).isEqualTo("Completed from DRAFT");
            assertThat(savedTransaction.getPerformedBy()).isEqualTo(user);
        }
    }
}
