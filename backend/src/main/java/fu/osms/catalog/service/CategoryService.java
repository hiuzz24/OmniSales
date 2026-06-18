package fu.osms.catalog.service;

import fu.osms.catalog.dto.request.CategoryRequest;
import fu.osms.catalog.dto.response.CategoryNodeResponse;
import fu.osms.catalog.dto.response.CategoryResponse;

import java.util.List;
import java.util.UUID;

public interface CategoryService {

    CategoryResponse create(CategoryRequest request);

    CategoryResponse getById(UUID id);

    List<CategoryResponse> getAll();

    List<CategoryResponse> getRootCategories();

    List<CategoryResponse> getSubCategories(UUID parentId);

    CategoryResponse update(UUID id, CategoryRequest request);

    void delete(UUID id);

    public List<CategoryNodeResponse> getCategoryTree();
}
