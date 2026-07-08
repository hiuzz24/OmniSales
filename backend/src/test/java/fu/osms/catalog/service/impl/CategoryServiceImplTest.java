package fu.osms.catalog.service.impl;

import fu.osms.catalog.dto.request.CategoryRequest;
import fu.osms.catalog.dto.response.CategoryDashboardResponse;
import fu.osms.catalog.dto.response.CategoryNodeResponse;
import fu.osms.catalog.dto.response.CategoryResponse;
import fu.osms.catalog.dto.response.CategoryResponseDTO;
import fu.osms.catalog.dto.response.CategoryProductCount;
import fu.osms.catalog.entity.Category;
import fu.osms.catalog.enums.CategoryStatus;
import fu.osms.catalog.mapper.CategoryDashboardMapper;
import fu.osms.catalog.mapper.CategoryMapper;
import fu.osms.catalog.repository.CategoryRepository;
import fu.osms.catalog.repository.ProductRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CategoryServiceImpl Tests")
class CategoryServiceImplTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CategoryMapper categoryMapper;

    @Mock
    private CategoryDashboardMapper categoryDashboardMapper;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private CategoryServiceImpl categoryService;

    private UUID categoryId;
    private UUID parentId;
    private Category testCategory;
    private Category parentCategory;
    private CategoryRequest testRequest;
    private CategoryResponse testResponse;

    @BeforeEach
    void setUp() {
        categoryId = UUID.randomUUID();
        parentId = UUID.randomUUID();

        parentCategory = Category.builder()
                .id(parentId)
                .name("Parent Category")
                .slug("parent-category")
                .status(CategoryStatus.ACTIVE)
                .sortOrder(0)
                .createdAt(OffsetDateTime.now())
                .build();

        testCategory = Category.builder()
                .id(categoryId)
                .name("Test Category")
                .slug("test-category")
                .status(CategoryStatus.ACTIVE)
                .sortOrder(1)
                .parent(parentCategory)
                .createdAt(OffsetDateTime.now())
                .build();

        testRequest = CategoryRequest.builder()
                .name("Test Category")
                .slug("test-category")
                .sortOrder(1)
                .build();

        testResponse = CategoryResponse.builder()
                .id(categoryId)
                .name("Test Category")
                .slug("test-category")
                .sortOrder(1)
                .createdAt(OffsetDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("create() Tests")
    class CreateTests {

        @Test
        @DisplayName("Should create category successfully without parent")
        void create_success() {
            when(categoryMapper.toEntity(testRequest)).thenReturn(testCategory);
            when(categoryRepository.save(any(Category.class))).thenReturn(testCategory);
            when(categoryMapper.toResponse(testCategory)).thenReturn(testResponse);

            CategoryResponse result = categoryService.create(testRequest);

            assertThat(result.getName()).isEqualTo("Test Category");
            verify(categoryRepository).save(any(Category.class));
        }

        @Test
        @DisplayName("Should create category with valid parent")
        void create_withParent() {
            testRequest.setParentId(parentId);

            when(categoryMapper.toEntity(testRequest)).thenReturn(testCategory);
            when(categoryRepository.findById(parentId)).thenReturn(Optional.of(parentCategory));
            when(categoryRepository.save(any(Category.class))).thenReturn(testCategory);
            when(categoryMapper.toResponse(testCategory)).thenReturn(testResponse);

            CategoryResponse result = categoryService.create(testRequest);

            assertThat(result).isNotNull();
            verify(categoryRepository).findById(parentId);
        }

        @Test
        @DisplayName("Should throw exception when parent not found")
        void create_parentNotFound() {
            testRequest.setParentId(parentId);

            when(categoryMapper.toEntity(testRequest)).thenReturn(testCategory);
            when(categoryRepository.findById(parentId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> categoryService.create(testRequest))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Parent category not found");
        }

        @Test
        @DisplayName("Should throw exception when creating active category under inactive parent")
        void create_withInactiveParent() {
            parentCategory.setStatus(CategoryStatus.INACTIVE);
            testRequest.setParentId(parentId);

            Category category = Category.builder()
                    .name("Test Category")
                    .slug("test-category")
                    .status(CategoryStatus.ACTIVE)
                    .parent(parentCategory)
                    .build();

            when(categoryMapper.toEntity(testRequest)).thenReturn(category);
            when(categoryRepository.findById(parentId)).thenReturn(Optional.of(parentCategory));

            assertThatThrownBy(() -> categoryService.create(testRequest))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Cannot create an active category under an inactive parent");
        }

        @Test
        @DisplayName("Should set default status to ACTIVE when not provided")
        void create_defaultStatus() {
            when(categoryMapper.toEntity(testRequest)).thenReturn(testCategory);
            when(categoryRepository.save(any(Category.class))).thenAnswer(i -> {
                Category c = i.getArgument(0);
                assertThat(c.getStatus()).isEqualTo(CategoryStatus.ACTIVE);
                return c;
            });
            when(categoryMapper.toResponse(any(Category.class))).thenReturn(testResponse);

            categoryService.create(testRequest);

            verify(categoryRepository).save(any(Category.class));
        }
    }

    @Nested
    @DisplayName("update() Tests")
    class UpdateTests {

        @Test
        @DisplayName("Should update category successfully")
        void update_success() {
            when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(testCategory));
            doNothing().when(categoryMapper).updateEntityFromRequest(testRequest, testCategory);
            when(categoryRepository.save(any(Category.class))).thenReturn(testCategory);
            when(categoryMapper.toResponse(testCategory)).thenReturn(testResponse);

            CategoryResponse result = categoryService.update(categoryId, testRequest);

            assertThat(result).isNotNull();
            verify(categoryRepository).save(any(Category.class));
        }

        @Test
        @DisplayName("Should throw exception when setting category as its own parent")
        void update_circularDependency() {
            testRequest.setParentId(categoryId);

            when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(testCategory));

            assertThatThrownBy(() -> categoryService.update(categoryId, testRequest))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("A category cannot be its own parent");
        }

        @Test
        @DisplayName("Should throw exception when parent not found during update")
        void update_parentNotFound() {
            testRequest.setParentId(parentId);

            when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(testCategory));
            when(categoryRepository.findById(parentId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> categoryService.update(categoryId, testRequest))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Parent category not found");
        }

        @Test
        @DisplayName("Should throw exception when setting active status with inactive parent")
        void update_withInactiveParent() {
            parentCategory.setStatus(CategoryStatus.INACTIVE);
            testRequest.setParentId(parentId);
            testRequest.setStatus("ACTIVE");

            Category category = Category.builder()
                    .name("Test Category")
                    .slug("test-category")
                    .status(CategoryStatus.ACTIVE)
                    .parent(parentCategory)
                    .build();

            when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
            when(categoryRepository.findById(parentId)).thenReturn(Optional.of(parentCategory));

            assertThatThrownBy(() -> categoryService.update(categoryId, testRequest))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Cannot set category to ACTIVE because its parent category is INACTIVE");
        }

        @Test
        @DisplayName("Should deactivate children when category becomes inactive")
        void update_inactiveChildrenRecursively() {
            testCategory.setStatus(CategoryStatus.ACTIVE);
            testRequest.setStatus("INACTIVE");

            Category child = Category.builder()
                    .id(UUID.randomUUID())
                    .name("Child Category")
                    .slug("child-category")
                    .status(CategoryStatus.ACTIVE)
                    .parent(testCategory)
                    .build();

            when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(testCategory));
            doAnswer(i -> {
                testCategory.setStatus(CategoryStatus.INACTIVE);
                return null;
            }).when(categoryMapper).updateEntityFromRequest(testRequest, testCategory);

            Category inactiveCategory = Category.builder()
                    .id(categoryId)
                    .name("Test Category")
                    .slug("test-category")
                    .status(CategoryStatus.INACTIVE)
                    .build();

            when(categoryRepository.save(any(Category.class))).thenReturn(inactiveCategory);
            when(categoryRepository.findByParentId(categoryId)).thenReturn(List.of(child));
            when(categoryMapper.toResponse(any(Category.class))).thenReturn(testResponse);

            categoryService.update(categoryId, testRequest);

            verify(categoryRepository, atLeastOnce()).save(any(Category.class));
        }

        @Test
        @DisplayName("Should throw exception when category not found")
        void update_categoryNotFound() {
            when(categoryRepository.findById(categoryId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> categoryService.update(categoryId, testRequest))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Category not found");
        }
    }

    @Nested
    @DisplayName("delete() Tests")
    class DeleteTests {

        @Test
        @DisplayName("Should soft delete category successfully")
        void delete_success() {
            when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(testCategory));
            when(categoryRepository.save(any(Category.class))).thenAnswer(i -> i.getArgument(0));
            when(categoryRepository.findByParentId(categoryId)).thenReturn(Collections.emptyList());

            categoryService.delete(categoryId);

            verify(categoryRepository).save(argThat(c -> c.getStatus() == CategoryStatus.INACTIVE));
        }

        @Test
        @DisplayName("Should deactivate children recursively when deleting")
        void delete_inactiveChildrenRecursively() {
            Category child = Category.builder()
                    .id(UUID.randomUUID())
                    .name("Child Category")
                    .slug("child-category")
                    .status(CategoryStatus.ACTIVE)
                    .parent(testCategory)
                    .build();

            when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(testCategory));
            when(categoryRepository.save(any(Category.class))).thenAnswer(i -> i.getArgument(0));
            when(categoryRepository.findByParentId(categoryId)).thenReturn(List.of(child));
            when(categoryRepository.findByParentId(child.getId())).thenReturn(Collections.emptyList());

            categoryService.delete(categoryId);

            verify(categoryRepository, atLeast(2)).save(any(Category.class));
        }

        @Test
        @DisplayName("Should throw exception when category not found")
        void delete_categoryNotFound() {
            when(categoryRepository.findById(categoryId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> categoryService.delete(categoryId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Category not found");
        }
    }

    @Nested
    @DisplayName("getCategoryTree() Tests")
    class GetCategoryTreeTests {

        @Test
        @DisplayName("Should build category tree successfully")
        void getCategoryTree_success() {
            Category parent = Category.builder()
                    .id(parentId)
                    .name("Parent")
                    .slug("parent")
                    .sortOrder(0)
                    .parent(null)
                    .build();

            Category child = Category.builder()
                    .id(categoryId)
                    .name("Child")
                    .slug("child")
                    .sortOrder(1)
                    .parent(parent)
                    .build();

            when(categoryRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of(parent, child));

            List<CategoryNodeResponse> result = categoryService.getCategoryTree();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("Parent");
            assertThat(result.get(0).getChildren()).hasSize(1);
            assertThat(result.get(0).getChildren().get(0).getName()).isEqualTo("Child");
        }

        @Test
        @DisplayName("Should handle orphaned nodes gracefully - not added to any parent")
        void getCategoryTree_orphanedNodes() {
            Category orphaned = Category.builder()
                    .id(categoryId)
                    .name("Orphaned")
                    .slug("orphaned")
                    .sortOrder(0)
                    .parent(testCategory)
                    .build();

            when(categoryRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of(orphaned));

            List<CategoryNodeResponse> result = categoryService.getCategoryTree();

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should return empty list when no categories")
        void getCategoryTree_empty() {
            when(categoryRepository.findAllByOrderBySortOrderAsc()).thenReturn(Collections.emptyList());

            List<CategoryNodeResponse> result = categoryService.getCategoryTree();

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getCategoryDashboardData() Tests")
    class GetCategoryDashboardDataTests {

        @Test
        @DisplayName("Should return dashboard data successfully")
        void getCategoryDashboardData_success() {
            PageRequest pageable = PageRequest.of(0, 10);
            Page<Category> categoryPage = new PageImpl<>(List.of(testCategory), pageable, 1);

            CategoryProductCount count = new CategoryProductCount() {
                @Override
                public UUID getCategoryId() { return categoryId; }
                @Override
                public Long getProductCount() { return 5L; }
            };

            CategoryResponseDTO dto = CategoryResponseDTO.builder()
                    .id(categoryId)
                    .name("Test Category")
                    .productCount(5)
                    .build();

            when(categoryRepository.findAll(pageable)).thenReturn(categoryPage);
            when(productRepository.countProductsByCategory()).thenReturn(List.of(count));
            when(categoryDashboardMapper.toResponseDTO(any(Category.class), anyLong())).thenReturn(dto);
            when(categoryRepository.count()).thenReturn(10L);
            when(categoryRepository.countByStatus(CategoryStatus.ACTIVE)).thenReturn(8L);
            when(categoryRepository.countByParentIsNull()).thenReturn(3L);
            when(productRepository.count()).thenReturn(100L);

            CategoryDashboardResponse result = categoryService.getCategoryDashboardData(pageable);

            assertThat(result.getTotalCategories()).isEqualTo(10);
            assertThat(result.getTotalActiveCategories()).isEqualTo(8);
            assertThat(result.getTotalParentCategories()).isEqualTo(3);
            assertThat(result.getTotalProducts()).isEqualTo(100);
            assertThat(result.getCategories()).hasSize(1);
        }

        @Test
        @DisplayName("Should handle empty dashboard data")
        void getCategoryDashboardData_empty() {
            PageRequest pageable = PageRequest.of(0, 10);
            Page<Category> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);

            when(categoryRepository.findAll(pageable)).thenReturn(emptyPage);
            when(productRepository.countProductsByCategory()).thenReturn(Collections.emptyList());
            when(categoryRepository.count()).thenReturn(0L);
            when(categoryRepository.countByStatus(CategoryStatus.ACTIVE)).thenReturn(0L);
            when(categoryRepository.countByParentIsNull()).thenReturn(0L);
            when(productRepository.count()).thenReturn(0L);

            CategoryDashboardResponse result = categoryService.getCategoryDashboardData(pageable);

            assertThat(result.getTotalCategories()).isZero();
            assertThat(result.getCategories()).isEmpty();
        }
    }

    @Nested
    @DisplayName("getAll() Tests")
    class GetAllTests {

        @Test
        @DisplayName("Should return all categories ordered by sortOrder")
        void getAll_success() {
            List<Category> categories = List.of(parentCategory, testCategory);
            List<CategoryResponse> responses = List.of(
                    CategoryResponse.builder().id(parentId).name("Parent").slug("parent").build(),
                    CategoryResponse.builder().id(categoryId).name("Test").slug("test").build()
            );

            when(categoryRepository.findAllByOrderBySortOrderAsc()).thenReturn(categories);
            when(categoryMapper.toResponse(parentCategory)).thenReturn(responses.get(0));
            when(categoryMapper.toResponse(testCategory)).thenReturn(responses.get(1));

            List<CategoryResponse> result = categoryService.getAll();

            assertThat(result).hasSize(2);
            verify(categoryRepository).findAllByOrderBySortOrderAsc();
        }

        @Test
        @DisplayName("Should return empty list when no categories")
        void getAll_empty() {
            when(categoryRepository.findAllByOrderBySortOrderAsc()).thenReturn(Collections.emptyList());

            List<CategoryResponse> result = categoryService.getAll();

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getById() Tests")
    class GetByIdTests {

        @Test
        @DisplayName("Should throw UnsupportedOperationException")
        void getById_throwsUnsupported() {
            assertThatThrownBy(() -> categoryService.getById(categoryId))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("getRootCategories() Tests")
    class GetRootCategoriesTests {

        @Test
        @DisplayName("Should throw UnsupportedOperationException")
        void getRootCategories_throwsUnsupported() {
            assertThatThrownBy(() -> categoryService.getRootCategories())
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("getSubCategories() Tests")
    class GetSubCategoriesTests {

        @Test
        @DisplayName("Should throw UnsupportedOperationException")
        void getSubCategories_throwsUnsupported() {
            assertThatThrownBy(() -> categoryService.getSubCategories(parentId))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
