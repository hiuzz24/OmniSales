package fu.osms.inventory.service;

import fu.osms.inventory.dto.request.StocktakeSessionRequest;
import fu.osms.inventory.dto.response.StocktakeSessionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;
import java.util.UUID;

public interface StocktakeService {

    StocktakeSessionResponse createStocktake(StocktakeSessionRequest request, boolean complete);

    StocktakeSessionResponse getStocktakeById(UUID id);

    Page<StocktakeSessionResponse> getStocktakes(UUID warehouseId, String status, String keyword, Pageable pageable);

    StocktakeSessionResponse updateStocktake(UUID id, StocktakeSessionRequest request);

    StocktakeSessionResponse changeStatus(UUID id, String status);

    Map<String, Object> getStatistics();

    int syncPendingMarketplaceInventory();

    int syncStocktakeMarketplaceInventory(UUID id);
}
