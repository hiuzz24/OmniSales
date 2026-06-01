package fu.osms.catalog.service;

import fu.osms.catalog.dto.request.CategoryRequest;
import fu.osms.catalog.dto.response.CategoryResponse;

import java.util.List;
import java.util.UUID;

public interface CategoryService {

    CategoryResponse create(CategoryRequest request);

    CategoryResponse getById(UUID id);

    List<CategoryResponse> getByShopId(UUID shopId);

    List<CategoryResponse> getRootCategories(UUID shopId);

    List<CategoryResponse> getSubCategories(UUID parentId);

    CategoryResponse update(UUID id, CategoryRequest request);

    void delete(UUID id);
}
