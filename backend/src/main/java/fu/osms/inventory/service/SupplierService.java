package fu.osms.inventory.service;

import fu.osms.common.dto.PageResponse;
import fu.osms.inventory.dto.response.SupplierResponse;

public interface SupplierService {

    PageResponse<SupplierResponse> getAll(int page, int size);
}
