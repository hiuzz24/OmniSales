package fu.osms.inventory.service;

import fu.osms.common.dto.PageResponse;
import fu.osms.inventory.dto.request.SupplierRequest;
import fu.osms.inventory.dto.response.SupplierResponse;

import java.util.UUID;

public interface SupplierService {

    PageResponse<SupplierResponse> getAll(int page, int size);

    PageResponse<SupplierResponse> searchActive(String keyword, int page, int size);

    SupplierResponse create(SupplierRequest request);

    SupplierResponse update(UUID id, SupplierRequest request);

    SupplierResponse updateStatus(UUID id, Boolean isActive);

}
