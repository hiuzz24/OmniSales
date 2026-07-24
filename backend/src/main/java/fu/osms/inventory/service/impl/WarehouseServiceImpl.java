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
        Warehouse warehouse = Warehouse.builder()
                .name(request.getName().trim())
                .address(request.getAddress() != null ? request.getAddress().trim() : null)
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .build();
        Warehouse saved = warehouseRepository.save(warehouse);
        return mapToResponseWithStats(saved);
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
        return getAll(null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WarehouseResponse> getAll(String keyword, String status) {
        Boolean isActive = null;
        if ("ACTIVE".equalsIgnoreCase(status)) {
            isActive = true;
        } else if ("INACTIVE".equalsIgnoreCase(status)) {
            isActive = false;
        }

        String kw = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;

        return warehouseRepository.searchWarehouses(kw, isActive)
                .stream()
                .map(this::mapToResponseWithStats)
                .toList();
    }

    @Override
    @Transactional
    public WarehouseResponse update(UUID id, WarehouseRequest request) {
        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> new fu.osms.common.exception.AppException(
                        fu.osms.common.exception.ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy kho hàng"));

        warehouse.setName(request.getName().trim());
        warehouse.setAddress(request.getAddress() != null ? request.getAddress().trim() : null);
        if (request.getIsActive() != null) {
            warehouse.setIsActive(request.getIsActive());
        }
        Warehouse updated = warehouseRepository.save(warehouse);
        return mapToResponseWithStats(updated);
    }

    @Override
    @Transactional
    public WarehouseResponse toggleStatus(UUID id, Boolean isActive) {
        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> new fu.osms.common.exception.AppException(
                        fu.osms.common.exception.ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy kho hàng"));

        warehouse.setIsActive(isActive);
        Warehouse updated = warehouseRepository.save(warehouse);
        return mapToResponseWithStats(updated);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> new fu.osms.common.exception.AppException(
                        fu.osms.common.exception.ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy kho hàng"));
        warehouse.setDeletedAt(java.time.OffsetDateTime.now());
        warehouseRepository.save(warehouse);
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
