package fu.osms.inventory.service.impl;

import fu.osms.common.dto.PageResponse;
import fu.osms.inventory.dto.request.WarehouseRequest;
import fu.osms.inventory.dto.response.WarehouseResponse;
import fu.osms.inventory.entity.Warehouse;
import fu.osms.inventory.mapper.WarehouseMapper;
import fu.osms.inventory.repository.WarehouseRepository;
import fu.osms.inventory.service.WarehouseService;
import fu.osms.shop.entity.Shop;
import fu.osms.shop.repository.ShopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WarehouseServiceImpl implements WarehouseService {

    private final WarehouseRepository warehouseRepository;
    private final ShopRepository shopRepository;
    private final WarehouseMapper warehouseMapper;

    @Override
    @Transactional
    public WarehouseResponse create(WarehouseRequest request) {
        Shop shop = shopRepository.findById(request.getShopId())
                .orElseThrow(() -> new RuntimeException("Shop not found: " + request.getShopId()));
        Warehouse warehouse = warehouseMapper.toEntity(request);
        warehouse.setShop(shop);
        return warehouseMapper.toResponse(warehouseRepository.save(warehouse));
    }

    @Override
    @Transactional(readOnly = true)
    public WarehouseResponse getById(UUID id) {
        return warehouseRepository.findById(id)
                .map(warehouseMapper::toResponse)
                .orElseThrow(() -> new RuntimeException("Warehouse not found: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<WarehouseResponse> getByShopId(UUID shopId) {
        return warehouseRepository.findByShopId(shopId)
                .stream().map(warehouseMapper::toResponse).toList();
    }

    @Override
    @Transactional
    public WarehouseResponse update(UUID id, WarehouseRequest request) {
        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Warehouse not found: " + id));
        warehouseMapper.updateEntityFromRequest(request, warehouse);
        return warehouseMapper.toResponse(warehouseRepository.save(warehouse));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        if (!warehouseRepository.existsById(id)) {
            throw new RuntimeException("Warehouse not found: " + id);
        }
        warehouseRepository.deleteById(id);
    }
}
