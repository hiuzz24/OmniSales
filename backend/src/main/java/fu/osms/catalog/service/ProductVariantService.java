package fu.osms.catalog.service;

import fu.osms.catalog.dto.response.ProductVariantResponse;
import fu.osms.common.dto.PageResponse;

public interface ProductVariantService {

    PageResponse<ProductVariantResponse> search(String keyword, int page, int size);
}
