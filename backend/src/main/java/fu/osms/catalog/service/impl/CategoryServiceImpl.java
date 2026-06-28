package fu.osms.catalog.service.impl;

import fu.osms.catalog.dto.request.CategoryRequest;
import fu.osms.catalog.dto.response.*;
import fu.osms.catalog.entity.Category;
import fu.osms.catalog.enums.CategoryStatus;
import fu.osms.catalog.mapper.CategoryDashboardMapper;
import fu.osms.catalog.mapper.CategoryMapper;
import fu.osms.catalog.repository.CategoryRepository;
import fu.osms.catalog.repository.ProductRepository;
import fu.osms.catalog.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;
    private final CategoryDashboardMapper categoryDashboardMapper;
    private final ProductRepository productRepository;
    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void dropCategoriesTrigger() {
        try {
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS trg_categories_updated_at ON categories;");
        } catch (Exception e) {
            System.err.println("Failed to drop trg_categories_updated_at trigger: " + e.getMessage());
        }
    }
    @Override
    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        Category category = categoryMapper.toEntity(request);

        if (request.getParentId() != null) {
            Category parent = categoryRepository.findById(request.getParentId())
                    .orElseThrow(() -> new IllegalArgumentException("Parent category not found"));
            category.setParent(parent);
        }

        if (request.getStatus() != null) {
            category.setStatus(CategoryStatus.valueOf(request.getStatus()));
        } else {
            category.setStatus(CategoryStatus.ACTIVE);
        }

        // Validate parent status constraint
        if (category.getParent() != null && category.getParent().getStatus() == CategoryStatus.INACTIVE) {
            if (category.getStatus() == CategoryStatus.ACTIVE) {
                throw new IllegalArgumentException("Cannot create an active category under an inactive parent category");
            }
            category.setStatus(CategoryStatus.INACTIVE);
        }

        Category saved = categoryRepository.save(category);
        return categoryMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse getById(UUID id) {
        throw new UnsupportedOperationException("Chưa code");
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getAll() {
        return categoryRepository.findAllByOrderBySortOrderAsc()
                .stream()
                .map(categoryMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getRootCategories() {
        throw new UnsupportedOperationException("Chưa code");
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getSubCategories(UUID parentId) {
        throw new UnsupportedOperationException("Chưa code");
    }

    @Override
    @Transactional
    public CategoryResponse update(UUID id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));

        categoryMapper.updateEntityFromRequest(request, category);

        if (request.getParentId() != null) {
            // Prevent circular dependency (setting category as its own parent)
            if (id.equals(request.getParentId())) {
                throw new IllegalArgumentException("A category cannot be its own parent");
            }
            Category parent = categoryRepository.findById(request.getParentId())
                    .orElseThrow(() -> new IllegalArgumentException("Parent category not found"));
            category.setParent(parent);
        } else {
            category.setParent(null);
        }

        if (request.getStatus() != null) {
            category.setStatus(CategoryStatus.valueOf(request.getStatus()));
        }

        // Validate parent status constraint
        if (category.getParent() != null && category.getParent().getStatus() == CategoryStatus.INACTIVE) {
            if (category.getStatus() == CategoryStatus.ACTIVE) {
                throw new IllegalArgumentException("Cannot set category to ACTIVE because its parent category is INACTIVE");
            }
            category.setStatus(CategoryStatus.INACTIVE);
        }

        Category saved = categoryRepository.save(category);

        if (saved.getStatus() == CategoryStatus.INACTIVE) {
            deactivateChildrenRecursively(saved.getId());
        }

        return categoryMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));
        category.setStatus(CategoryStatus.INACTIVE);
        categoryRepository.save(category);
        deactivateChildrenRecursively(id);
    }

    private void deactivateChildrenRecursively(UUID parentId) {
        List<Category> children = categoryRepository.findByParentId(parentId);
        for (Category child : children) {
            if (child.getStatus() != CategoryStatus.INACTIVE) {
                child.setStatus(CategoryStatus.INACTIVE);
                categoryRepository.save(child);
                deactivateChildrenRecursively(child.getId());
            }
        }
    }

    @Override
    public List<CategoryNodeResponse> getCategoryTree() {
        List<Category> allCategories = categoryRepository.findAllByOrderBySortOrderAsc();

        List<CategoryNodeResponse> allNodes = allCategories.stream()
                .map(category -> CategoryNodeResponse.builder()
                        .id(category.getId())
                        .name(category.getName())
                        .slug(category.getSlug())
                        .sortOrder(category.getSortOrder())
                        .children(new ArrayList<>())
                        .build())
                .collect(Collectors.toList());

        Map<UUID, CategoryNodeResponse> nodeMap = allNodes.stream()
                .collect(Collectors.toMap(CategoryNodeResponse::getId, node -> node));
        List<CategoryNodeResponse> rootCategories = new ArrayList<>();

        for (Category category : allCategories) {
            CategoryNodeResponse currentNode = nodeMap.get(category.getId());

            if (category.getParent() == null) {
                rootCategories.add(currentNode);
            } else {
                UUID parentId = category.getParent().getId();
                CategoryNodeResponse parentNode = nodeMap.get(parentId);

                if (parentNode != null) {
                    parentNode.getChildren().add(currentNode);
                }
            }
        }

        return rootCategories;
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryDashboardResponse getCategoryDashboardData(Pageable pageable) {
        Page<Category> page = categoryRepository.findAll(pageable);

        Map<UUID, Long> productCountMap =
                productRepository.countProductsByCategory()
                        .stream()
                        .collect(Collectors.toMap(
                                CategoryProductCount::getCategoryId,
                                CategoryProductCount::getProductCount
                        ));

        List<CategoryResponseDTO> categories =
                page.getContent()
                        .stream()
                        .map(category -> categoryDashboardMapper.toResponseDTO(
                                category,
                                productCountMap.getOrDefault(category.getId(), 0L)
                        ))
                        .toList();

        return CategoryDashboardResponse.builder()
                .totalCategories(categoryRepository.count())
                .totalActiveCategories(categoryRepository.countByStatus(CategoryStatus.ACTIVE))
                .totalParentCategories(categoryRepository.countByParentIsNull())
                .totalProducts(productRepository.count())
                .categories(categories)
                .totalPages(page.getTotalPages())
                .totalElements(page.getTotalElements())
                .build();
    }
}
