package fu.osms.catalog.service;

import fu.osms.catalog.dto.response.ProductLogResponse;
import fu.osms.common.dto.PageResponse;

import java.util.UUID;

public interface ProductLogService {
    PageResponse<ProductLogResponse> getLogs(UUID productId, int page, int size, String sort);
}
