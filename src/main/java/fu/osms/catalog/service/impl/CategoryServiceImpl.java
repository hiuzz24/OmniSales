package fu.osms.catalog.service.impl;

import fu.osms.catalog.dto.request.CategoryRequest;
import fu.osms.catalog.dto.response.CategoryResponse;
import fu.osms.catalog.entity.Category;
import fu.osms.catalog.mapper.CategoryMapper;
import fu.osms.catalog.repository.CategoryRepository;
import fu.osms.catalog.service.CategoryService;
import fu.osms.shop.entity.Shop;
import fu.osms.shop.repository.ShopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final ShopRepository shopRepository;
    private final CategoryMapper categoryMapper;

    @Override
    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        Shop shop = shopRepository.findById(request.getShopId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy shop: " + request.getShopId()));
        if (categoryRepository.existsByShopIdAndSlug(request.getShopId(), request.getSlug())) {
            throw new IllegalArgumentException("Slug đã tồn tại trong shop");
        }
        Category category = categoryMapper.toEntity(request);
        category.setShop(shop);
        if (request.getParentId() != null) {
            Category parent = categoryRepository.findById(request.getParentId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy danh mục cha"));
            category.setParent(parent);
        }
        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse getById(UUID id) {
        return categoryRepository.findById(id)
                .map(categoryMapper::toResponse)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy danh mục: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getByShopId(UUID shopId) {
        return categoryRepository.findByShopIdOrderBySortOrderAsc(shopId)
                .stream().map(categoryMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getRootCategories(UUID shopId) {
        return categoryRepository.findByShopIdAndParentIsNull(shopId)
                .stream().map(categoryMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getSubCategories(UUID parentId) {
        return categoryRepository.findByParentId(parentId)
                .stream().map(categoryMapper::toResponse).toList();
    }

    @Override
    @Transactional
    public CategoryResponse update(UUID id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy danh mục: " + id));
        categoryMapper.updateEntityFromRequest(request, category);
        if (request.getParentId() != null) {
            Category parent = categoryRepository.findById(request.getParentId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy danh mục cha"));
            category.setParent(parent);
        } else {
            category.setParent(null);
        }
        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        if (!categoryRepository.existsById(id)) {
            throw new RuntimeException("Không tìm thấy danh mục: " + id);
        }
        categoryRepository.deleteById(id);
    }
}
