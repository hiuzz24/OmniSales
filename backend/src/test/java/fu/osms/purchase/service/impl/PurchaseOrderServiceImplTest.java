package fu.osms.purchase.service.impl;

import fu.osms.auth.entity.User;
import fu.osms.auth.repository.UserRepository;
import fu.osms.auth.repository.UserRoleRepository;
import fu.osms.catalog.entity.Product;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.catalog.repository.ProductVariantRepository;
import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelProductVariant;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.common.dto.PageResponse;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.exception.AppException;
import fu.osms.inventory.entity.InventoryItem;
import fu.osms.inventory.entity.Supplier;
import fu.osms.inventory.entity.Warehouse;
import fu.osms.inventory.repository.InventoryItemRepository;
import fu.osms.inventory.repository.SupplierRepository;
import fu.osms.notification.service.NotificationService;
import fu.osms.purchase.dto.*;
import fu.osms.purchase.entity.PurchaseOrder;
import fu.osms.purchase.entity.PurchaseOrderItem;
import fu.osms.purchase.enums.PurchaseOrderStatus;
import fu.osms.purchase.repository.PurchaseOrderRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PurchaseOrderServiceImpl Tests")
class PurchaseOrderServiceImplTest {

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;
    @Mock
    private SupplierRepository supplierRepository;
    @Mock
    private ProductVariantRepository variantRepository;
    @Mock
    private InventoryItemRepository inventoryItemRepository;
    @Mock
    private ChannelRepository channelRepository;
    @Mock
    private ChannelProductVariantRepository channelVariantRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserRoleRepository userRoleRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private MarketplaceWarehouseConsistencyService warehouseConsistencyService;

    @InjectMocks
    private PurchaseOrderServiceImpl purchaseOrderService;

    // Test data
    private UUID userId;
    private UUID supplierId;
    private UUID warehouseId;
    private UUID variantId;
    private UUID channelId;
    private UUID orderId;

    private User user;
    private Supplier supplier;
    private Warehouse warehouse;
    private Product product;
    private ProductVariant variant;
    private Channel channel;
    private PurchaseOrder purchaseOrder;
    private PurchaseOrderItem purchaseOrderItem;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        supplierId = UUID.randomUUID();
        warehouseId = UUID.randomUUID();
        variantId = UUID.randomUUID();
        channelId = UUID.randomUUID();
        orderId = UUID.randomUUID();

        user = User.builder()
                .id(userId)
                .email("manager@osms.vn")
                .fullName("Manager User")
                .build();

        supplier = Supplier.builder()
                .id(supplierId)
                .name("Test Supplier")
                .supplierCode("SUP-001")
                .build();

        warehouse = Warehouse.builder()
                .id(warehouseId)
                .name("Main Warehouse")
                .address("123 Test Street")
                .isActive(true)
                .build();

        product = Product.builder()
                .id(UUID.randomUUID())
                .name("Test Product")
                .build();

        variant = ProductVariant.builder()
                .id(variantId)
                .sku("TEST-VAR-001")
                .name("Test Variant")
                .price(new BigDecimal("150000"))
                .costPrice(new BigDecimal("100000"))
                .isActive(true)
                .deletedAt(null)
                .product(product)
                .build();

        channel = Channel.builder()
                .id(channelId)
                .displayName("TikTok Shop")
                .platform(PlatformType.TIKTOK)
                .status("CONNECTED")
                .syncEnabled(true)
                .metadata(new HashMap<>() {{
                    put("tiktokWarehouseId", "WH-001");
                }})
                .build();

        purchaseOrderItem = PurchaseOrderItem.builder()
                .id(UUID.randomUUID())
                .variant(variant)
                .quantity(10)
                .unitCost(new BigDecimal("100000"))
                .build();

        purchaseOrder = PurchaseOrder.builder()
                .id(orderId)
                .orderCode("MĐH-2026-000001")
                .supplier(supplier)
                .warehouse(warehouse)
                .status(PurchaseOrderStatus.DRAFT)
                .orderDate(OffsetDateTime.now())
                .expectedReceiptDate(LocalDate.now().plusDays(7))
                .totalAmount(new BigDecimal("1000000"))
                .createdBy(user)
                .items(new ArrayList<>(List.of(purchaseOrderItem)))
                .build();
        purchaseOrderItem.setPurchaseOrder(purchaseOrder);
    }

    // =========================================================
    // create() Tests
    // =========================================================
    @Nested
    @DisplayName("create() Tests")
    class CreateTests {

        @Test
        @DisplayName("Should create DRAFT purchase order successfully")
        void shouldCreateDraftOrderSuccessfully() {
            PurchaseOrderRequest request = createValidRequest(true);

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(supplier));
            when(warehouseConsistencyService.resolveMasterWarehouse()).thenReturn(warehouse);
            when(variantRepository.findById(variantId)).thenReturn(Optional.of(variant));
            when(channelVariantRepository.findActiveByVariantIdWithChannel(variantId)).thenReturn(List.of());
            when(channelVariantRepository.findActiveByVariantIdInWithChannel(anyList())).thenReturn(List.of());
            when(channelVariantRepository.findActiveByNormalizedExternalSkuInWithVariant(anyList())).thenReturn(List.of());
            when(purchaseOrderRepository.existsByOrderCode(anyString())).thenReturn(false);
            when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(invocation -> {
                PurchaseOrder order = invocation.getArgument(0);
                order.setId(orderId);
                order.setOrderCode("MĐH-2026-000001");
                return order;
            });

            PurchaseOrderResponse result = purchaseOrderService.create(request, userId);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(orderId);
            assertThat(result.getStatus()).isEqualTo(PurchaseOrderStatus.DRAFT);
            verify(purchaseOrderRepository).save(any(PurchaseOrder.class));
        }

        @Test
        @DisplayName("Should create SENT_TO_SUPPLIER purchase order when isDraft=false")
        void shouldCreateSentToSupplierOrderWhenNotDraft() {
            PurchaseOrderRequest request = createValidRequest(false);

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(supplier));
            when(warehouseConsistencyService.resolveMasterWarehouse()).thenReturn(warehouse);
            when(variantRepository.findById(variantId)).thenReturn(Optional.of(variant));
            when(channelVariantRepository.findActiveByVariantIdWithChannel(variantId)).thenReturn(List.of());
            when(channelVariantRepository.findActiveByVariantIdInWithChannel(anyList())).thenReturn(List.of());
            when(channelVariantRepository.findActiveByNormalizedExternalSkuInWithVariant(anyList())).thenReturn(List.of());
            when(purchaseOrderRepository.existsByOrderCode(anyString())).thenReturn(false);
            when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(invocation -> {
                PurchaseOrder order = invocation.getArgument(0);
                order.setId(orderId);
                return order;
            });

            PurchaseOrderResponse result = purchaseOrderService.create(request, userId);

            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(PurchaseOrderStatus.SENT_TO_SUPPLIER);
        }

        @Test
        @DisplayName("Should throw when user not found")
        void shouldThrowWhenUserNotFound() {
            PurchaseOrderRequest request = createValidRequest(true);

            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> purchaseOrderService.create(request, userId))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("Không tìm thấy người dùng");
        }

        @Test
        @DisplayName("Should throw when supplier not found")
        void shouldThrowWhenSupplierNotFound() {
            PurchaseOrderRequest request = createValidRequest(true);

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(supplierRepository.findById(supplierId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> purchaseOrderService.create(request, userId))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("Không tìm thấy nhà cung cấp");
        }

        @Test
        @DisplayName("Should throw when items list is empty")
        void shouldThrowWhenItemsListEmpty() {
            PurchaseOrderRequest request = createValidRequest(true);
            request.setItems(List.of());

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(supplier));
            when(warehouseConsistencyService.resolveMasterWarehouse()).thenReturn(warehouse);

            assertThatThrownBy(() -> purchaseOrderService.create(request, userId))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("Đơn mua hàng phải có ít nhất một sản phẩm");
        }

        @Test
        @DisplayName("Should throw when variant not found")
        void shouldThrowWhenVariantNotFound() {
            PurchaseOrderRequest request = createValidRequest(true);

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(supplier));
            when(warehouseConsistencyService.resolveMasterWarehouse()).thenReturn(warehouse);
            when(variantRepository.findById(variantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> purchaseOrderService.create(request, userId))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("Không tìm thấy biến thể sản phẩm");
        }

        @Test
        @DisplayName("Should throw when duplicate variants in request")
        void shouldThrowWhenDuplicateVariants() {
            PurchaseOrderRequest request = createValidRequest(true);
            request.setItems(List.of(
                    createItemRequest(variantId, 5, new BigDecimal("100000")),
                    createItemRequest(variantId, 3, new BigDecimal("100000"))
            ));

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(supplier));
            when(warehouseConsistencyService.resolveMasterWarehouse()).thenReturn(warehouse);
            when(variantRepository.findById(variantId)).thenReturn(Optional.of(variant));
            when(channelVariantRepository.findActiveByVariantIdWithChannel(variantId)).thenReturn(List.of());
            when(channelVariantRepository.findActiveByVariantIdInWithChannel(anyList())).thenReturn(List.of());
            when(channelVariantRepository.findActiveByNormalizedExternalSkuInWithVariant(anyList())).thenReturn(List.of());

            assertThatThrownBy(() -> purchaseOrderService.create(request, userId))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("Sản phẩm bị trùng");
        }
    }

    // =========================================================
    // updateDraft() Tests
    // =========================================================
    @Nested
    @DisplayName("updateDraft() Tests")
    class UpdateDraftTests {

        @Test
        @DisplayName("Should update DRAFT order successfully")
        void shouldUpdateDraftOrderSuccessfully() {
            PurchaseOrderRequest request = createValidRequest(true);
            request.setNotes("Updated notes");

            when(purchaseOrderRepository.findByIdWithDetails(orderId)).thenReturn(Optional.of(purchaseOrder));
            when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(supplier));
            when(variantRepository.findById(variantId)).thenReturn(Optional.of(variant));
            when(channelVariantRepository.findActiveByVariantIdWithChannel(variantId)).thenReturn(List.of());
            when(channelVariantRepository.findActiveByVariantIdInWithChannel(anyList())).thenReturn(List.of());
            when(channelVariantRepository.findActiveByNormalizedExternalSkuInWithVariant(anyList())).thenReturn(List.of());
            when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenReturn(purchaseOrder);

            PurchaseOrderResponse result = purchaseOrderService.updateDraft(orderId, request);

            assertThat(result).isNotNull();
            verify(purchaseOrderRepository).save(any(PurchaseOrder.class));
        }

        @Test
        @DisplayName("Should throw when order not found for update")
        void shouldThrowWhenOrderNotFoundForUpdate() {
            PurchaseOrderRequest request = createValidRequest(true);

            when(purchaseOrderRepository.findByIdWithDetails(orderId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> purchaseOrderService.updateDraft(orderId, request))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("Không tìm thấy đơn mua hàng");
        }

        @Test
        @DisplayName("Should throw when updating non-DRAFT order")
        void shouldThrowWhenUpdatingNonDraftOrder() {
            purchaseOrder.setStatus(PurchaseOrderStatus.SENT_TO_SUPPLIER);
            PurchaseOrderRequest request = createValidRequest(true);

            when(purchaseOrderRepository.findByIdWithDetails(orderId)).thenReturn(Optional.of(purchaseOrder));

            assertThatThrownBy(() -> purchaseOrderService.updateDraft(orderId, request))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("Chỉ được sửa đơn mua hàng ở trạng thái Nháp");
        }
    }

    // =========================================================
    // sendToSupplier() Tests
    // =========================================================
    @Nested
    @DisplayName("sendToSupplier() Tests")
    class SendToSupplierTests {

        @Test
        @DisplayName("Should send DRAFT order to supplier successfully")
        void shouldSendDraftOrderToSupplierSuccessfully() {
            purchaseOrder.setStatus(PurchaseOrderStatus.DRAFT);

            when(purchaseOrderRepository.findByIdWithDetails(orderId)).thenReturn(Optional.of(purchaseOrder));
            when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenReturn(purchaseOrder);

            PurchaseOrderResponse result = purchaseOrderService.sendToSupplier(orderId);

            assertThat(result).isNotNull();
            verify(purchaseOrderRepository).save(any(PurchaseOrder.class));
        }

        @Test
        @DisplayName("Should throw when sending non-DRAFT order")
        void shouldThrowWhenSendingNonDraftOrder() {
            purchaseOrder.setStatus(PurchaseOrderStatus.SENT_TO_SUPPLIER);

            when(purchaseOrderRepository.findByIdWithDetails(orderId)).thenReturn(Optional.of(purchaseOrder));

            assertThatThrownBy(() -> purchaseOrderService.sendToSupplier(orderId))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("Chỉ đơn Nháp mới có thể gửi nhà cung cấp");
        }

        @Test
        @DisplayName("Should throw when order not found")
        void shouldThrowWhenOrderNotFound() {
            when(purchaseOrderRepository.findByIdWithDetails(orderId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> purchaseOrderService.sendToSupplier(orderId))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("Không tìm thấy đơn mua hàng");
        }
    }

    // =========================================================
    // cancel() Tests
    // =========================================================
    @Nested
    @DisplayName("cancel() Tests")
    class CancelTests {

        @Test
        @DisplayName("Should cancel DRAFT order successfully")
        void shouldCancelDraftOrderSuccessfully() {
            purchaseOrder.setStatus(PurchaseOrderStatus.DRAFT);

            when(purchaseOrderRepository.findByIdWithDetails(orderId)).thenReturn(Optional.of(purchaseOrder));
            when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenReturn(purchaseOrder);

            PurchaseOrderResponse result = purchaseOrderService.cancel(orderId);

            assertThat(result).isNotNull();
            assertThat(purchaseOrder.getStatus()).isEqualTo(PurchaseOrderStatus.CANCELLED);
            verify(purchaseOrderRepository).save(any(PurchaseOrder.class));
        }

        @Test
        @DisplayName("Should cancel SENT_TO_SUPPLIER order successfully")
        void shouldCancelSentToSupplierOrderSuccessfully() {
            purchaseOrder.setStatus(PurchaseOrderStatus.SENT_TO_SUPPLIER);

            when(purchaseOrderRepository.findByIdWithDetails(orderId)).thenReturn(Optional.of(purchaseOrder));
            when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenReturn(purchaseOrder);

            PurchaseOrderResponse result = purchaseOrderService.cancel(orderId);

            assertThat(result).isNotNull();
            assertThat(purchaseOrder.getStatus()).isEqualTo(PurchaseOrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("Should throw when cancelling COMPLETED order")
        void shouldThrowWhenCancellingCompletedOrder() {
            purchaseOrder.setStatus(PurchaseOrderStatus.COMPLETED);

            when(purchaseOrderRepository.findByIdWithDetails(orderId)).thenReturn(Optional.of(purchaseOrder));

            assertThatThrownBy(() -> purchaseOrderService.cancel(orderId))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("Không thể hủy đơn mua hàng đã có phiếu nhập kho");
        }

        @Test
        @DisplayName("Should throw when cancelling order with receipt")
        void shouldThrowWhenCancellingOrderWithReceipt() {
            purchaseOrder.setStatus(PurchaseOrderStatus.RECEIVING);
            purchaseOrder.setReceipt(fu.osms.inventory.entity.InventoryReceipt.builder()
                    .id(UUID.randomUUID())
                    .build());

            when(purchaseOrderRepository.findByIdWithDetails(orderId)).thenReturn(Optional.of(purchaseOrder));

            assertThatThrownBy(() -> purchaseOrderService.cancel(orderId))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("Không thể hủy đơn mua hàng đã có phiếu nhập kho");
        }
    }

    // =========================================================
    // getById() Tests
    // =========================================================
    @Nested
    @DisplayName("getById() Tests")
    class GetByIdTests {

        @Test
        @DisplayName("Should get order by ID successfully")
        void shouldGetOrderByIdSuccessfully() {
            when(purchaseOrderRepository.findByIdWithDetails(orderId)).thenReturn(Optional.of(purchaseOrder));

            PurchaseOrderResponse result = purchaseOrderService.getById(orderId);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(orderId);
            assertThat(result.getOrderCode()).isEqualTo("MĐH-2026-000001");
        }

        @Test
        @DisplayName("Should throw when order not found")
        void shouldThrowWhenOrderNotFound() {
            when(purchaseOrderRepository.findByIdWithDetails(orderId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> purchaseOrderService.getById(orderId))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("Không tìm thấy đơn mua hàng");
        }
    }

    // =========================================================
    // getAll() Tests
    // =========================================================
    @Nested
    @DisplayName("getAll() Tests")
    class GetAllTests {

        @Test
        @DisplayName("Should get all orders with pagination")
        void shouldGetAllOrdersWithPagination() {
            Page<PurchaseOrder> page = new PageImpl<>(List.of(purchaseOrder), PageRequest.of(0, 10), 1);
            when(purchaseOrderRepository.findAllWithDetails(PageRequest.of(0, 10))).thenReturn(page);

            PageResponse<PurchaseOrderResponse> result = purchaseOrderService.getAll(0, 10, null);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should get orders filtered by status")
        void shouldGetOrdersFilteredByStatus() {
            Page<PurchaseOrder> page = new PageImpl<>(List.of(purchaseOrder), PageRequest.of(0, 10), 1);
            when(purchaseOrderRepository.findAllWithDetailsByStatus(eq(PurchaseOrderStatus.DRAFT), any(PageRequest.class)))
                    .thenReturn(page);

            PageResponse<PurchaseOrderResponse> result = purchaseOrderService.getAll(0, 10, PurchaseOrderStatus.DRAFT);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
        }
    }

    // =========================================================
    // getStatistics() Tests
    // =========================================================
    @Nested
    @DisplayName("getStatistics() Tests")
    class GetStatisticsTests {

        @Test
        @DisplayName("Should return order statistics")
        void shouldReturnOrderStatistics() {
            when(purchaseOrderRepository.count()).thenReturn(100L);
            when(purchaseOrderRepository.countByStatus(PurchaseOrderStatus.DRAFT)).thenReturn(10L);
            when(purchaseOrderRepository.countByStatus(PurchaseOrderStatus.SENT_TO_SUPPLIER)).thenReturn(20L);
            when(purchaseOrderRepository.countByStatus(PurchaseOrderStatus.RECEIVING)).thenReturn(15L);
            when(purchaseOrderRepository.countByStatus(PurchaseOrderStatus.COMPLETED)).thenReturn(50L);
            when(purchaseOrderRepository.countByStatus(PurchaseOrderStatus.CANCELLED)).thenReturn(5L);

            Map<String, Long> result = purchaseOrderService.getStatistics();

            assertThat(result).isNotNull();
            assertThat(result.get("totalCount")).isEqualTo(100L);
            assertThat(result.get("DRAFT")).isEqualTo(10L);
            assertThat(result.get("SENT_TO_SUPPLIER")).isEqualTo(20L);
            assertThat(result.get("RECEIVING")).isEqualTo(15L);
            assertThat(result.get("COMPLETED")).isEqualTo(50L);
            assertThat(result.get("CANCELLED")).isEqualTo(5L);
        }
    }

    // =========================================================
    // generateOrderCode() Tests
    // =========================================================
    @Nested
    @DisplayName("generateOrderCode() Tests")
    class GenerateOrderCodeTests {

        @Test
        @DisplayName("Should generate unique order code with correct format")
        void shouldGenerateUniqueOrderCodeWithCorrectFormat() {
            when(purchaseOrderRepository.existsByOrderCode(anyString())).thenReturn(false);

            String result = purchaseOrderService.generateOrderCode();

            assertThat(result).isNotNull();
            assertThat(result).startsWith("MĐH-" + LocalDate.now().getYear() + "-");
            assertThat(result).matches("MĐH-\\d{4}-\\d{6}");
        }

        @Test
        @DisplayName("Should generate code with correct format")
        void shouldGenerateCodeWithCorrectFormat() {
            when(purchaseOrderRepository.existsByOrderCode(anyString())).thenReturn(false);

            String result = purchaseOrderService.generateOrderCode();

            assertThat(result).isNotNull();
            assertThat(result).startsWith("MĐH-" + LocalDate.now().getYear() + "-");
            assertThat(result).matches("MĐH-\\d{4}-\\d{6}");
        }
    }

    // =========================================================
    // moveSentOrdersToReceiving() Tests
    // =========================================================
    @Nested
    @DisplayName("moveSentOrdersToReceiving() Tests")
    class MoveSentOrdersToReceivingTests {

        @Test
        @DisplayName("Should move sent orders to receiving status")
        void shouldMoveSentOrdersToReceivingStatus() {
            PurchaseOrder sentOrder1 = PurchaseOrder.builder()
                    .id(UUID.randomUUID())
                    .orderCode("MĐH-2026-000001")
                    .status(PurchaseOrderStatus.SENT_TO_SUPPLIER)
                    .sentAt(OffsetDateTime.now().minusSeconds(15))
                    .build();
            PurchaseOrder sentOrder2 = PurchaseOrder.builder()
                    .id(UUID.randomUUID())
                    .orderCode("MĐH-2026-000002")
                    .status(PurchaseOrderStatus.SENT_TO_SUPPLIER)
                    .sentAt(OffsetDateTime.now().minusSeconds(20))
                    .build();

            when(purchaseOrderRepository.findByStatusAndSentAtLessThanEqual(
                    eq(PurchaseOrderStatus.SENT_TO_SUPPLIER), any(OffsetDateTime.class)))
                    .thenReturn(List.of(sentOrder1, sentOrder2));
            when(purchaseOrderRepository.saveAll(anyList())).thenReturn(List.of(sentOrder1, sentOrder2));
            when(userRoleRepository.findByRoleNameIn(anyList())).thenReturn(List.of());

            int result = purchaseOrderService.moveSentOrdersToReceiving();

            assertThat(result).isEqualTo(2);
            assertThat(sentOrder1.getStatus()).isEqualTo(PurchaseOrderStatus.RECEIVING);
            assertThat(sentOrder2.getStatus()).isEqualTo(PurchaseOrderStatus.RECEIVING);
            assertThat(sentOrder1.getReceivingAt()).isNotNull();
            assertThat(sentOrder2.getReceivingAt()).isNotNull();
        }

        @Test
        @DisplayName("Should return zero when no orders to move")
        void shouldReturnZeroWhenNoOrdersToMove() {
            when(purchaseOrderRepository.findByStatusAndSentAtLessThanEqual(
                    eq(PurchaseOrderStatus.SENT_TO_SUPPLIER), any(OffsetDateTime.class)))
                    .thenReturn(List.of());

            int result = purchaseOrderService.moveSentOrdersToReceiving();

            assertThat(result).isEqualTo(0);
        }
    }

    // =========================================================
    // completeFromReceipt() Tests
    // =========================================================
    @Nested
    @DisplayName("completeFromReceipt() Tests")
    class CompleteFromReceiptTests {

        @Test
        @DisplayName("Should complete order from receipt successfully")
        void shouldCompleteOrderFromReceiptSuccessfully() {
            purchaseOrder.setStatus(PurchaseOrderStatus.RECEIVING);

            when(purchaseOrderRepository.findByIdWithDetails(orderId)).thenReturn(Optional.of(purchaseOrder));
            when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenReturn(purchaseOrder);

            purchaseOrderService.completeFromReceipt(orderId);

            assertThat(purchaseOrder.getStatus()).isEqualTo(PurchaseOrderStatus.COMPLETED);
            assertThat(purchaseOrder.getCompletedAt()).isNotNull();
            verify(purchaseOrderRepository).save(purchaseOrder);
        }

        @Test
        @DisplayName("Should throw when order is not in RECEIVING status")
        void shouldThrowWhenOrderNotInReceivingStatus() {
            purchaseOrder.setStatus(PurchaseOrderStatus.SENT_TO_SUPPLIER);

            when(purchaseOrderRepository.findByIdWithDetails(orderId)).thenReturn(Optional.of(purchaseOrder));

            assertThatThrownBy(() -> purchaseOrderService.completeFromReceipt(orderId))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("Đơn mua hàng phải ở trạng thái Đang giao hàng trước khi hoàn thành phiếu nhập");
        }
    }

    // =========================================================
    // Helper Methods
    // =========================================================

    private PurchaseOrderRequest createValidRequest(boolean isDraft) {
        PurchaseOrderRequest request = new PurchaseOrderRequest();
        request.setSupplierId(supplierId);
        request.setExpectedReceiptDate(LocalDate.now().plusDays(7));
        request.setPaymentMethod("CASH");
        request.setNotes("Test notes");
        request.setIsDraft(isDraft);
        request.setItems(List.of(createItemRequest(variantId, 10, new BigDecimal("100000"))));
        return request;
    }

    private PurchaseOrderItemRequest createItemRequest(UUID variantId, int quantity, BigDecimal unitCost) {
        PurchaseOrderItemRequest item = new PurchaseOrderItemRequest();
        item.setVariantId(variantId);
        item.setQuantity(quantity);
        item.setUnitCost(unitCost);
        return item;
    }
}
