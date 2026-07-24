package fu.osms.inventory.service.impl;

import fu.osms.auth.repository.UserRepository;
import fu.osms.inventory.entity.Warehouse;
import fu.osms.inventory.dto.request.WarehouseRequest;
import fu.osms.inventory.dto.response.WarehouseResponse;
import fu.osms.inventory.mapper.WarehouseMapper;
import fu.osms.inventory.repository.InventoryItemRepository;
import fu.osms.inventory.repository.WarehouseRepository;
import fu.osms.inventory.service.WarehouseService;
import fu.osms.sync.service.MarketplaceWarehouseConsistencyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WarehouseServiceImpl implements WarehouseService {

    private final WarehouseRepository warehouseRepository;
    private final UserRepository userRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final WarehouseMapper warehouseMapper;
    private final MarketplaceWarehouseConsistencyService marketplaceWarehouseConsistencyService;

    @Override
    @Transactional
    public WarehouseResponse create(WarehouseRequest request) {
        throw new UnsupportedOperationException("Chưa code");
    }

    @Override
    @Transactional(readOnly = true)
    public WarehouseResponse getById(UUID id) {
        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> new fu.osms.common.exception.AppException(fu.osms.common.exception.ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy kho hàng"));
        return mapToResponseWithStats(warehouse);
    }

    @Override
    @Transactional(readOnly = true)
    public WarehouseResponse getMaster() {
        return mapToResponseWithStats(marketplaceWarehouseConsistencyService.resolveMasterWarehouse());
    }

    @Override
    @Transactional(readOnly = true)
    public List<WarehouseResponse> getAll() {
        return warehouseRepository.findByIsActiveTrueOrderByNameAsc()
                .stream()
                .map(this::mapToResponseWithStats)
                .toList();
    }

    @Override
    @Transactional
    public WarehouseResponse update(UUID id, WarehouseRequest request) {
        throw new UnsupportedOperationException("Chưa code");
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        throw new UnsupportedOperationException("Chưa code");
    }

    @Override
    public WarehouseResponse getWarehouseByUserId(UUID userId) {
        return mapToResponseWithStats(warehouseRepository.findWarehouseByUserId(userId));
    }

    private WarehouseResponse mapToResponseWithStats(Warehouse warehouse) {
        if (warehouse == null) return null;
        WarehouseResponse response = warehouseMapper.toResponse(warehouse);
        if (response != null && warehouse.getId() != null) {
            response.setStaffCount(userRepository.countByWarehouseId(warehouse.getId()));
            response.setProductCount(inventoryItemRepository.countProductTypesByWarehouseId(warehouse.getId()));
            response.setTotalStock(inventoryItemRepository.sumTotalStockByWarehouseId(warehouse.getId()));
        }
        return response;
    }
}
