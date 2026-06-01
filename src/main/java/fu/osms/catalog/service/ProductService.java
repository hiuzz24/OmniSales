package fu.osms.catalog.service;

import fu.osms.catalog.dto.request.ProductRequest;
import fu.osms.catalog.dto.response.ProductResponse;
import fu.osms.catalog.enums.ProductStatus;
import fu.osms.common.dto.PageResponse;

import java.util.UUID;

public interface ProductService {

    ProductResponse create(ProductRequest request);

    ProductResponse getById(UUID id);

    PageResponse<ProductResponse> getByShopId(UUID shopId, int page, int size);

    PageResponse<ProductResponse> search(UUID shopId, String keyword, ProductStatus status, int page, int size);

    ProductResponse update(UUID id, ProductRequest request);

    ProductResponse updateStatus(UUID id, ProductStatus status);

    void delete(UUID id);
}
