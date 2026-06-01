package fu.osms.inventory.service;

import fu.osms.inventory.dto.request.WarehouseRequest;
import fu.osms.inventory.dto.response.WarehouseResponse;

import java.util.List;
import java.util.UUID;

public interface WarehouseService {

    WarehouseResponse create(WarehouseRequest request);

    WarehouseResponse getById(UUID id);

    List<WarehouseResponse> getByShopId(UUID shopId);

    WarehouseResponse update(UUID id, WarehouseRequest request);

    void delete(UUID id);
}
