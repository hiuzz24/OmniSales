package fu.osms.catalog.service;

import fu.osms.catalog.dto.response.ProductInsightsResponse;

import java.util.UUID;

public interface ProductInsightsService {
    ProductInsightsResponse getInsights(UUID productId);
}
