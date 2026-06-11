package fu.osms.customer.service.impl;

import fu.osms.common.dto.PageResponse;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.customer.dto.request.CustomerRequest;
import fu.osms.customer.dto.response.CustomerResponse;
import fu.osms.customer.entity.Customer;
import fu.osms.customer.mapper.CustomerMapper;
import fu.osms.customer.repository.CustomerRepository;
import fu.osms.customer.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;

    @Override
    @Transactional
    public CustomerResponse create(CustomerRequest request) {
        throw new UnsupportedOperationException("Chưa code");
    }

    @Override
    public CustomerResponse getById(UUID id) {
        throw new UnsupportedOperationException("Chưa code");
    }

    @Override
    public PageResponse<CustomerResponse> getAll(int page, int size, String search) {
        throw new UnsupportedOperationException("Chưa code");
    }

    @Override
    @Transactional
    public CustomerResponse update(UUID id, CustomerRequest request) {
        throw new UnsupportedOperationException("Chưa code");
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        throw new UnsupportedOperationException("Chưa code");
    }

    private Customer findById(UUID id) {
        throw new UnsupportedOperationException("Chưa code");
    }
}
