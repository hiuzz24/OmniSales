package fu.osms.customer.service;

import fu.osms.common.dto.PageResponse;
import fu.osms.customer.dto.request.CustomerRequest;
import fu.osms.customer.dto.response.CustomerResponse;

import java.util.UUID;

public interface CustomerService {

    CustomerResponse create(CustomerRequest request);

    CustomerResponse getById(UUID id);

    PageResponse<CustomerResponse> getAll(int page, int size, String search);

    CustomerResponse update(UUID id, CustomerRequest request);

    void delete(UUID id);
}
