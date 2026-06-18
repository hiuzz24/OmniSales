package fu.osms.catalog.service.impl;

import fu.osms.catalog.dto.request.CategoryRequest;
import fu.osms.catalog.dto.response.CategoryNodeResponse;
import fu.osms.catalog.dto.response.CategoryResponse;
import fu.osms.catalog.entity.Category;
import fu.osms.catalog.mapper.CategoryMapper;
import fu.osms.catalog.repository.CategoryRepository;
import fu.osms.catalog.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Override
    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        throw new UnsupportedOperationException("Chưa code");
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
        throw new UnsupportedOperationException("Chưa code");
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        throw new UnsupportedOperationException("Chưa code");
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

}
