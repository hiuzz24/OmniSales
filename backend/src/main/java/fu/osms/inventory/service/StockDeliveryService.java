package fu.osms.inventory.service;

import fu.osms.inventory.dto.request.StockDeliveryRequest;
import fu.osms.inventory.dto.response.StockDeliveryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.UUID;

public interface StockDeliveryService {

    /**
     * Create a new stock delivery document
     * @param request Stock delivery request
     * @return Created stock delivery response
     */
    StockDeliveryResponse createStockDelivery(StockDeliveryRequest request);

    /**
     * Update a draft stock delivery document
     * @param id Delivery ID
     * @param request Stock delivery request
     * @return Updated stock delivery response
     */
    StockDeliveryResponse updateStockDelivery(UUID id, StockDeliveryRequest request);

    /**
     * Get stock delivery by ID
     * @param id Delivery ID
     * @return Stock delivery response
     */
    StockDeliveryResponse getStockDeliveryById(UUID id);

    /**
     * Get all stock deliveries with filters and pagination
     * @param warehouseId Warehouse ID filter (optional)
     * @param status Status filter (optional)
     * @param deliveryType Delivery type filter (optional)
     * @param startDate Start date filter (optional)
     * @param endDate End date filter (optional)
     * @param pageable Pagination information
     * @return Page of stock deliveries
     */
    Page<StockDeliveryResponse> getAllStockDeliveries(
            UUID warehouseId,
            String status,
            String deliveryType,
            String keyword,
            LocalDate startDate,
            LocalDate endDate,
            Pageable pageable
    );

    /**
     * Confirm stock delivery (change status from DRAFT to CONFIRMED)
     * @param id Delivery ID
     * @return Updated stock delivery response
     */
    StockDeliveryResponse confirmStockDelivery(UUID id);

    /**
     * Cancel stock delivery
     * @param id Delivery ID
     * @return Updated stock delivery response
     */
    StockDeliveryResponse cancelStockDelivery(UUID id);

    /**
     * Get delivery statistics
     * @return Delivery statistics
     */
    Object getDeliveryStatistics();

    int syncPendingMarketplaceInventory();
}
