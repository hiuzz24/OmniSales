package fu.osms.inventory.service.impl;

import fu.osms.common.dto.PageResponse;
import fu.osms.inventory.dto.request.WarehouseRequest;
import fu.osms.inventory.dto.response.WarehouseResponse;
import fu.osms.inventory.entity.Warehouse;
import fu.osms.inventory.mapper.WarehouseMapper;
import fu.osms.inventory.repository.WarehouseRepository;
import fu.osms.inventory.service.WarehouseService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WarehouseServiceImpl implements WarehouseService {

    private final WarehouseRepository warehouseRepository;
    private final WarehouseMapper warehouseMapper;

    @Override
    @Transactional
    public WarehouseResponse create(WarehouseRequest request) {
        throw new UnsupportedOperationException("Chưa code");
    }

    @Override
    @Transactional(readOnly = true)
    public WarehouseResponse getById(UUID id) {
        throw new UnsupportedOperationException("Chưa code");
    }

    @Override
    @Transactional(readOnly = true)
    public List<WarehouseResponse> getAll() {
        return warehouseRepository.findByIsActiveTrueOrderByNameAsc()
                .stream()
                .map(warehouseMapper::toResponse)
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
        return warehouseMapper.toResponse(warehouseRepository.findWarehouseByUserId(userId));
    }
}
