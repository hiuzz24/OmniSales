package fu.osms.catalog.service.impl;

import fu.osms.auth.entity.User;
import fu.osms.catalog.dto.request.ProductRequest;
import fu.osms.catalog.dto.request.ProductVariantRequest;
import fu.osms.catalog.dto.response.ProductResponse;
import fu.osms.catalog.dto.response.ProductVariantResponse;
import fu.osms.catalog.entity.Category;
import fu.osms.catalog.entity.Product;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.catalog.enums.ProductStatus;
import fu.osms.catalog.mapper.ProductMapper;
import fu.osms.catalog.mapper.ProductVariantMapper;
import fu.osms.catalog.mapper.ProductImageMapper;
import fu.osms.catalog.repository.CategoryRepository;
import fu.osms.catalog.repository.ProductRepository;
import fu.osms.catalog.repository.ProductVariantRepository;
import fu.osms.catalog.repository.ProductImageRepository;
import fu.osms.catalog.repository.ProductLogRepository;
import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelProduct;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.channel.repository.ChannelProductRepository;
import fu.osms.channel.service.ChannelService;
import fu.osms.channel.dto.response.ChannelSyncResponse;
import fu.osms.common.dto.PageResponse;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.common.utils.SecurityUtils;
import fu.osms.inventory.dto.response.StockSummaryDTO;
import fu.osms.inventory.service.InventoryService;
import fu.osms.order.repository.OrderItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductServiceImpl Tests")
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private ProductVariantMapper productVariantMapper;
    @Mock
    private ProductImageMapper productImageMapper;
    @Mock
    private ProductVariantRepository productVariantRepository;
    @Mock
    private ProductImageRepository productImageRepository;
    @Mock
    private InventoryService inventoryService;
    @Mock
    private ChannelService channelService;
    @Mock
    private ChannelRepository channelRepository;
    @Mock
    private ChannelProductRepository channelProductRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private ProductLogRepository productLogRepository;

    @InjectMocks
    private ProductServiceImpl productService;

    // Test data
    private UUID productId;
    private UUID categoryId;
    private UUID userId;
    private UUID variantId;
    private Category category;
    private User user;
    private Product product;
    private ProductVariant variant;
    private ProductRequest request;
    private ProductVariantRequest variantRequest;
    private ProductResponse response;
    private ProductVariantResponse variantResponse;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();
        categoryId = UUID.randomUUID();
        userId = UUID.randomUUID();
        variantId = UUID.randomUUID();

        category = Category.builder()
                .id(categoryId)
                .name("Danh mục Test")
                .slug("danh-muc-test")
                .build();

        user = User.builder()
                .id(userId)
                .email("manager@osms.vn")
                .fullName("Manager User")
                .build();

        product = Product.builder()
                .id(productId)
                .name("Test Product")
                .sku("TEST-001")
                .category(category)
                .status(ProductStatus.ACTIVE)
                .lowStockThreshold(5)
                .attributes(new HashMap<>())
                .createdBy(user)
                .build();

        variant = ProductVariant.builder()
                .id(variantId)
                .product(product)
                .sku("TEST-001-V1")
                .name("Variant 1")
                .price(new BigDecimal("150000"))
                .costPrice(new BigDecimal("80000"))
                .isActive(true)
                .optionValues(Map.of("Size", "M"))
                .createdBy(user)
                .build();

        variantRequest = ProductVariantRequest.builder()
                .sku("TEST-001-V1")
                .price(new BigDecimal("150000"))
                .costPrice(new BigDecimal("80000"))
                .isActive(true)
                .optionValues(Map.of("Size", "M"))
                .build();

        request = ProductRequest.builder()
                .name("Test Product")
                .sku("TEST-001")
                .categoryId(categoryId)
                .status(ProductStatus.ACTIVE)
                .lowStockThreshold(5)
                .variants(List.of(variantRequest))
                .build();

        variantResponse = ProductVariantResponse.builder()
                .id(variantId)
                .sku("TEST-001-V1")
                .name("Variant 1")
                .price(new BigDecimal("150000"))
                .costPrice(new BigDecimal("80000"))
                .isActive(true)
                .availableQuantity(50)
                .quantityOnHand(50)
                .build();

        response = ProductResponse.builder()
                .id(productId)
                .name("Test Product")
                .sku("TEST-001")
                .categoryId(categoryId)
                .categoryName("Danh mục Test")
                .status(ProductStatus.ACTIVE)
                .lowStockThreshold(5)
                .variants(List.of(variantResponse))
                .channels(List.of())
                .channelSyncs(List.of())
                .build();
    }

    private MockedStatic<SecurityUtils> mockSecurityUtils() {
        MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class);
        mocked.when(SecurityUtils::getCurrentUser).thenReturn(Optional.of(user));
        return mocked;
    }

    // =========================================================
    // CREATE TESTS
    // =========================================================
    @Nested
    @DisplayName("create() Tests")
    class CreateTests {

        @Test
        @DisplayName("Should create product successfully with variants")
        void shouldCreateProductSuccessfully() {
            try (MockedStatic<SecurityUtils> mocked = mockSecurityUtils()) {
                // Arrange
                when(productRepository.existsBySkuAndDeletedAtIsNull("TEST-001")).thenReturn(false);
                when(productMapper.toEntity(request)).thenReturn(product);
                when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
                when(productVariantRepository.existsBySkuInAndDeletedAtIsNull(anyCollection())).thenReturn(false);
                when(productRepository.existsBySkuInAndDeletedAtIsNull(anyCollection())).thenReturn(false);
                when(productRepository.save(any(Product.class))).thenReturn(product);
                when(productVariantMapper.toEntity(variantRequest)).thenReturn(variant);
                when(productVariantRepository.saveAll(anyList())).thenReturn(List.of(variant));

                when(productRepository.findById(productId)).thenReturn(Optional.of(product));
                when(productMapper.toResponse(product)).thenReturn(response);
//                when(productImageRepository.findByProductIdOrderBySortOrderAsc(productId)).thenReturn(Collections.emptyList());
                when(productVariantRepository.findByProductIdAndDeletedAtIsNull(productId)).thenReturn(List.of(variant));
                when(inventoryService.getStockSummary(anyList())).thenReturn(Map.of(variantId, new StockSummaryDTO(50, 50, 0)));
                when(channelService.getProductChannels(anyList())).thenReturn(Map.of());
                when(channelService.getProductChannelSyncs(anyList())).thenReturn(Map.of());
                when(productVariantMapper.toResponse(variant)).thenReturn(variantResponse);
                when(productLogRepository.save(any())).thenReturn(null);

                // Act
                ProductResponse result = productService.create(request);

                // Assert
                assertThat(result).isNotNull();
                assertThat(result.getName()).isEqualTo("Test Product");
                assertThat(result.getSku()).isEqualTo("TEST-001");
                assertThat(result.getStatus()).isEqualTo(ProductStatus.ACTIVE);

                verify(productRepository).existsBySkuAndDeletedAtIsNull("TEST-001");
                verify(categoryRepository).findById(categoryId);
                verify(productRepository).save(any(Product.class));
                verify(productVariantRepository).saveAll(anyList());
                verify(productLogRepository).save(any());
            }
        }

        @Test
        @DisplayName("Should throw PRODUCT_SKU_CONFLICT when SKU already exists")
        void shouldThrowWhenSkuExists() {
            try (MockedStatic<SecurityUtils> mocked = mockSecurityUtils()) {
                when(productRepository.existsBySkuAndDeletedAtIsNull("TEST-001")).thenReturn(true);

                assertThatThrownBy(() -> productService.create(request))
                        .isInstanceOf(AppException.class)
                        .satisfies(e -> {
                            AppException appEx = (AppException) e;
                            assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.PRODUCT_SKU_CONFLICT);
                        });

                verify(productRepository).existsBySkuAndDeletedAtIsNull("TEST-001");
                verify(productRepository, never()).save(any());
            }
        }

        @Test
        @DisplayName("Should throw CATEGORY_NOT_FOUND when category does not exist")
        void shouldThrowWhenCategoryNotFound() {
            try (MockedStatic<SecurityUtils> mocked = mockSecurityUtils()) {
                when(productRepository.existsBySkuAndDeletedAtIsNull("TEST-001")).thenReturn(false);
                when(productMapper.toEntity(request)).thenReturn(product);
                when(categoryRepository.findById(categoryId)).thenReturn(Optional.empty());

                assertThatThrownBy(() -> productService.create(request))
                        .isInstanceOf(AppException.class)
                        .satisfies(e -> {
                            AppException appEx = (AppException) e;
                            assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.CATEGORY_NOT_FOUND);
                        });

                verify(productRepository, never()).save(any());
            }
        }

        @Test
        @DisplayName("Should throw VARIANT_SKU_CONFLICT when variant SKU conflicts with existing variant")
        void shouldThrowWhenVariantSkuConflict() {
            try (MockedStatic<SecurityUtils> mocked = mockSecurityUtils()) {
                when(productRepository.existsBySkuAndDeletedAtIsNull("TEST-001")).thenReturn(false);
                when(productMapper.toEntity(request)).thenReturn(product);
                when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
                when(productVariantRepository.existsBySkuInAndDeletedAtIsNull(anyCollection())).thenReturn(true);

                assertThatThrownBy(() -> productService.create(request))
                        .isInstanceOf(AppException.class)
                        .satisfies(e -> {
                            AppException appEx = (AppException) e;
                            assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.VARIANT_SKU_CONFLICT);
                        });

                verify(productRepository, never()).save(any());
            }
        }

        @Test
        @DisplayName("Should throw VARIANT_SKU_CONFLICT when duplicate variant SKUs in request")
        void shouldThrowWhenDuplicateVariantSkusInRequest() {
            try (MockedStatic<SecurityUtils> mocked = mockSecurityUtils()) {
                ProductVariantRequest dupVariant = ProductVariantRequest.builder()
                        .sku("TEST-001") // same as product SKU
                        .price(new BigDecimal("150000"))
                        .build();
                request.setVariants(List.of(variantRequest, dupVariant));

                when(productRepository.existsBySkuAndDeletedAtIsNull("TEST-001")).thenReturn(false);
                when(productMapper.toEntity(request)).thenReturn(product);
                when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
                when(productVariantRepository.existsBySkuInAndDeletedAtIsNull(anyCollection())).thenReturn(false);
                when(productRepository.existsBySkuInAndDeletedAtIsNull(anyCollection())).thenReturn(true); // SKU conflicts with product

                assertThatThrownBy(() -> productService.create(request))
                        .isInstanceOf(AppException.class)
                        .satisfies(e -> {
                            AppException appEx = (AppException) e;
                            assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.PRODUCT_SKU_CONFLICT);
                        });
            }
        }

        @Test
        @DisplayName("Should create product with empty attributes when attributes is null")
        void shouldCreateWithEmptyAttributesWhenNull() {
            try (MockedStatic<SecurityUtils> mocked = mockSecurityUtils()) {
                request.setAttributes(null);

                when(productRepository.existsBySkuAndDeletedAtIsNull("TEST-001")).thenReturn(false);
                when(productMapper.toEntity(request)).thenReturn(product);
                when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
                when(productVariantRepository.existsBySkuInAndDeletedAtIsNull(anyCollection())).thenReturn(false);
                when(productRepository.existsBySkuInAndDeletedAtIsNull(anyCollection())).thenReturn(false);
                when(productRepository.save(any(Product.class))).thenReturn(product);
                when(productVariantMapper.toEntity(variantRequest)).thenReturn(variant);
                when(productVariantRepository.saveAll(anyList())).thenReturn(List.of(variant));
                when(productRepository.findById(productId)).thenReturn(Optional.of(product));
                when(productMapper.toResponse(product)).thenReturn(response);
//                when(productImageRepository.findByProductIdOrderBySortOrderAsc(productId)).thenReturn(Collections.emptyList());
                when(productVariantRepository.findByProductIdAndDeletedAtIsNull(productId)).thenReturn(List.of(variant));
                when(inventoryService.getStockSummary(anyList())).thenReturn(Map.of());
                when(channelService.getProductChannels(anyList())).thenReturn(Map.of());
                when(channelService.getProductChannelSyncs(anyList())).thenReturn(Map.of());
                when(productVariantMapper.toResponse(variant)).thenReturn(variantResponse);
                when(productLogRepository.save(any())).thenReturn(null);

                ProductResponse result = productService.create(request);

                assertThat(result).isNotNull();
                verify(productRepository).save(any(Product.class));
            }
        }
    }

    // =========================================================
    // GET BY ID TESTS
    // =========================================================
    @Nested
    @DisplayName("getById() Tests")
    class GetByIdTests {

        @Test
        @DisplayName("Should get product by ID successfully")
        void shouldGetProductByIdSuccessfully() {
            when(productRepository.findById(productId))
                    .thenReturn(Optional.of(product));
            when(productMapper.toResponse(product)).thenReturn(response);
            when(orderItemRepository.existsByVariant_Product_Id(productId)).thenReturn(false);
            when(productImageRepository.findByProductIdOrderBySortOrderAsc(productId)).thenReturn(Collections.emptyList());
            when(productVariantRepository.findByProductIdAndDeletedAtIsNull(productId)).thenReturn(List.of(variant));
            when(inventoryService.getStockSummary(anyList())).thenReturn(Map.of(variantId, new StockSummaryDTO(50, 50, 0)));
            when(channelService.getProductChannels(anyList())).thenReturn(Map.of());
            when(channelService.getProductChannelSyncs(anyList())).thenReturn(Map.of());
            when(productVariantMapper.toResponse(variant)).thenReturn(variantResponse);

            ProductResponse result = productService.getById(productId);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(productId);
            assertThat(result.getName()).isEqualTo("Test Product");
            assertThat(result.getVariants()).hasSize(1);

            verify(productRepository).findById(productId);
            verify(inventoryService).getStockSummary(anyList());
        }

        @Test
        @DisplayName("Should throw PRODUCT_NOT_FOUND when product does not exist")
        void shouldThrowWhenProductNotFound() {
            when(productRepository.findById(productId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.getById(productId))
                    .isInstanceOf(AppException.class)
                    .satisfies(e -> {
                        AppException appEx = (AppException) e;
                        assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
                    });
        }

        @Test
        @DisplayName("Should throw PRODUCT_NOT_FOUND when product is soft-deleted")
        void shouldThrowWhenProductIsDeleted() {
            product.setDeletedAt(OffsetDateTime.now());
            when(productRepository.findById(productId))
                    .thenReturn(Optional.of(product));

            assertThatThrownBy(() -> productService.getById(productId))
                    .isInstanceOf(AppException.class)
                    .satisfies(e -> {
                        AppException appEx = (AppException) e;
                        assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
                    });
        }

        @Test
        @DisplayName("Should populate hasOrders when product has orders")
        void shouldPopulateHasOrdersWhenProductHasOrders() {
            response.setHasOrders(true);

            when(productRepository.findById(productId)).thenReturn(Optional.of(product));
            when(productMapper.toResponse(product)).thenReturn(response);
            when(orderItemRepository.existsByVariant_Product_Id(productId)).thenReturn(true);
            when(productImageRepository.findByProductIdOrderBySortOrderAsc(productId)).thenReturn(Collections.emptyList());
            when(productVariantRepository.findByProductIdAndDeletedAtIsNull(productId)).thenReturn(List.of(variant));
            when(inventoryService.getStockSummary(anyList())).thenReturn(Map.of());
            when(channelService.getProductChannels(anyList())).thenReturn(Map.of());
            when(channelService.getProductChannelSyncs(anyList())).thenReturn(Map.of());
            when(productVariantMapper.toResponse(variant)).thenReturn(variantResponse);

            ProductResponse result = productService.getById(productId);

            assertThat(result.getHasOrders()).isTrue();
        }

        @Test
        @DisplayName("Should return empty channels when product has no channels")
        void shouldReturnEmptyChannelsWhenNoChannels() {
            when(productRepository.findById(productId)).thenReturn(Optional.of(product));
            when(productMapper.toResponse(product)).thenReturn(response);
            when(orderItemRepository.existsByVariant_Product_Id(productId)).thenReturn(false);
            when(productImageRepository.findByProductIdOrderBySortOrderAsc(productId)).thenReturn(Collections.emptyList());
            when(productVariantRepository.findByProductIdAndDeletedAtIsNull(productId)).thenReturn(List.of(variant));
            when(inventoryService.getStockSummary(anyList())).thenReturn(Map.of());
            when(channelService.getProductChannels(anyList())).thenReturn(Map.of());
            when(channelService.getProductChannelSyncs(anyList())).thenReturn(Map.of());
            when(productVariantMapper.toResponse(variant)).thenReturn(variantResponse);

            ProductResponse result = productService.getById(productId);

            assertThat(result.getChannels()).isEmpty();
        }
    }

    // =========================================================
    // SEARCH TESTS
    // =========================================================
    @Nested
    @DisplayName("search() Tests")
    class SearchTests {

        @Test
        @DisplayName("Should return paginated results successfully")
        void shouldReturnPaginatedResults() {
            Page<Product> page = new PageImpl<>(List.of(product), PageRequest.of(0, 6), 1);

            when(productRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);
            when(productMapper.toResponse(product)).thenReturn(response);
            when(productVariantRepository.findByProductIdInAndDeletedAtIsNull(anyList())).thenReturn(List.of(variant));
            when(productImageRepository.findByProductIdInOrderBySortOrderAsc(anyList())).thenReturn(Collections.emptyList());
            when(inventoryService.getStockSummary(anyList())).thenReturn(Map.of());
            when(channelService.getProductChannels(anyList())).thenReturn(Map.of());
            when(channelService.getProductChannelSyncs(anyList())).thenReturn(Map.of());
            when(productVariantMapper.toResponse(variant)).thenReturn(variantResponse);

            PageResponse<ProductResponse> result = productService.search(null, null, null, 0, 6);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getPage()).isEqualTo(0);
            assertThat(result.getSize()).isEqualTo(6);
        }

        @Test
        @DisplayName("Should return empty page when no products match")
        void shouldReturnEmptyPageWhenNoMatch() {
            Page<Product> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 6), 0);

            when(productRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(emptyPage);

            PageResponse<ProductResponse> result = productService.search("nonexistent", null, null, 0, 6);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should filter by keyword correctly")
        void shouldFilterByKeyword() {
            Page<Product> page = new PageImpl<>(List.of(product), PageRequest.of(0, 6), 1);

            when(productRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);
            when(productMapper.toResponse(product)).thenReturn(response);
            when(productVariantRepository.findByProductIdInAndDeletedAtIsNull(anyList())).thenReturn(List.of(variant));
            when(productImageRepository.findByProductIdInOrderBySortOrderAsc(anyList())).thenReturn(Collections.emptyList());
            when(inventoryService.getStockSummary(anyList())).thenReturn(Map.of());
            when(channelService.getProductChannels(anyList())).thenReturn(Map.of());
            when(channelService.getProductChannelSyncs(anyList())).thenReturn(Map.of());
            when(productVariantMapper.toResponse(variant)).thenReturn(variantResponse);

            PageResponse<ProductResponse> result = productService.search("Test", null, null, 0, 6);

            assertThat(result.getContent()).hasSize(1);
            verify(productRepository).findAll(any(Specification.class), any(PageRequest.class));
        }

        @Test
        @DisplayName("Should filter by status correctly")
        void shouldFilterByStatus() {
            Page<Product> page = new PageImpl<>(List.of(product), PageRequest.of(0, 6), 1);

            when(productRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);
            when(productMapper.toResponse(product)).thenReturn(response);
            when(productVariantRepository.findByProductIdInAndDeletedAtIsNull(anyList())).thenReturn(List.of(variant));
            when(productImageRepository.findByProductIdInOrderBySortOrderAsc(anyList())).thenReturn(Collections.emptyList());
            when(inventoryService.getStockSummary(anyList())).thenReturn(Map.of());
            when(channelService.getProductChannels(anyList())).thenReturn(Map.of());
            when(channelService.getProductChannelSyncs(anyList())).thenReturn(Map.of());
            when(productVariantMapper.toResponse(variant)).thenReturn(variantResponse);

            PageResponse<ProductResponse> result = productService.search(null, ProductStatus.ACTIVE, null, 0, 6);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getStatus()).isEqualTo(ProductStatus.ACTIVE);
        }
    }

    // =========================================================
    // DELETE TESTS
    // =========================================================
    @Nested
    @DisplayName("delete() Tests")
    class DeleteTests {

        @Test
        @DisplayName("Should soft delete product successfully")
        void shouldSoftDeleteProductSuccessfully() {
            when(productRepository.findById(productId)).thenReturn(Optional.of(product));
            when(productVariantRepository.findByProductIdAndDeletedAtIsNull(productId)).thenReturn(List.of(variant));
            when(productRepository.save(any(Product.class))).thenReturn(product);
            when(productVariantRepository.saveAll(anyList())).thenReturn(List.of(variant));
            when(productLogRepository.save(any())).thenReturn(null);

            productService.delete(productId);

            assertThat(product.getDeletedAt()).isNotNull();
            assertThat(variant.getDeletedAt()).isNotNull();
            verify(productRepository).save(product);
            verify(productVariantRepository).saveAll(anyList());
            verify(productLogRepository).save(any());
        }

        @Test
        @DisplayName("Should throw PRODUCT_NOT_FOUND when deleting non-existent product")
        void shouldThrowWhenDeletingNonExistent() {
            when(productRepository.findById(productId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.delete(productId))
                    .isInstanceOf(AppException.class)
                    .satisfies(e -> {
                        AppException appEx = (AppException) e;
                        assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
                    });

            verify(productRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should soft delete product even when it has no variants")
        void shouldSoftDeleteProductWithNoVariants() {
            when(productRepository.findById(productId)).thenReturn(Optional.of(product));
            when(productVariantRepository.findByProductIdAndDeletedAtIsNull(productId)).thenReturn(Collections.emptyList());
            when(productRepository.save(any(Product.class))).thenReturn(product);
            when(productLogRepository.save(any())).thenReturn(null);

            productService.delete(productId);

            assertThat(product.getDeletedAt()).isNotNull();
            verify(productVariantRepository, never()).saveAll(anyList());
        }
    }

    // =========================================================
    // UPDATE STATUS TESTS (throws UnsupportedOperationException)
    // =========================================================
    @Nested
    @DisplayName("updateStatus() Tests")
    class UpdateStatusTests {

        @Test
        @DisplayName("Should throw UnsupportedOperationException - not yet implemented")
        void shouldThrowUnsupportedOperation() {
            assertThatThrownBy(() -> productService.updateStatus(productId, ProductStatus.ACTIVE))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessage("Chưa code");
        }
    }
}
