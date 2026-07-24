package fu.osms.inventory.service;

import fu.osms.inventory.dto.request.WarehouseRequest;
import fu.osms.inventory.dto.response.WarehouseResponse;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WarehouseService {

    WarehouseResponse create(WarehouseRequest request);

    WarehouseResponse getById(UUID id);

    WarehouseResponse getMaster();

    List<WarehouseResponse> getAll();

    List<WarehouseResponse> getAll(String keyword, String status);

    WarehouseResponse update(UUID id, WarehouseRequest request);

    WarehouseResponse toggleStatus(UUID id, Boolean isActive);

    void delete(UUID id);

    WarehouseResponse getWarehouseByUserId(UUID userId);
}
