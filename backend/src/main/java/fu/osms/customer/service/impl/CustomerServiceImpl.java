package fu.osms.customer.service.impl;

import fu.osms.common.dto.PageResponse;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.customer.dto.request.CustomerRequest;
import fu.osms.customer.dto.response.CustomerResponse;
import fu.osms.customer.dto.response.CustomerStatsResponse;
import fu.osms.customer.entity.Customer;
import fu.osms.customer.mapper.CustomerMapper;
import fu.osms.customer.repository.CustomerRepository;
import fu.osms.customer.service.CustomerService;
import fu.osms.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;
    private final OrderRepository orderRepository;

    @Override
    @Transactional
    public CustomerResponse create(CustomerRequest request) {
        if (request.getPhone() != null && customerRepository.findByPhone(request.getPhone()).isPresent()) {
            throw new AppException(ErrorCode.CONFLICT, "Số điện thoại đã được sử dụng");
        }
        if (request.getEmail() != null && customerRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new AppException(ErrorCode.CONFLICT, "Email đã được sử dụng");
        }

        Customer customer = customerMapper.toEntity(request);
        if (customer.getIsActive() == null) {
            customer.setIsActive(true);
        }
        Customer savedCustomer = customerRepository.save(customer);
        return toCustomerResponse(savedCustomer);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerResponse getById(UUID id) {
        Customer customer = findById(id);
        return toCustomerResponse(customer);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> getAll(int page, int size, String search, String status, String gender) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Customer> customerPage;

        boolean hasSearch = search != null && !search.trim().isEmpty();
        boolean hasStatus = status != null && !"ALL".equalsIgnoreCase(status);
        boolean hasGender = gender != null && !"ALL".equalsIgnoreCase(gender);

        // Normalize gender: accept both enum (MALE/FEMALE/OTHER) and Vietnamese labels
        String genderValue = gender;
        if (hasGender) {
            genderValue = switch (gender.toUpperCase()) {
                case "MALE" -> "Nam";
                case "FEMALE" -> "Nữ";
                case "OTHER" -> "Khác";
                default -> gender; // already Vietnamese label
            };
        }

        if (hasSearch && hasStatus && hasGender) {
            customerPage = customerRepository.findAllBySearchKeywordAndStatusAndGender(
                    "%" + search.trim().toLowerCase() + "%", "ACTIVE".equalsIgnoreCase(status), genderValue, pageRequest);
        } else if (hasSearch && hasStatus) {
            customerPage = customerRepository.findAllBySearchKeywordAndStatus(
                    "%" + search.trim().toLowerCase() + "%", "ACTIVE".equalsIgnoreCase(status), pageRequest);
        } else if (hasSearch && hasGender) {
            customerPage = customerRepository.findAllBySearchKeywordAndGender(
                    "%" + search.trim().toLowerCase() + "%", genderValue, pageRequest);
        } else if (hasStatus && hasGender) {
            customerPage = customerRepository.findAllByIsActiveAndGender(
                    "ACTIVE".equalsIgnoreCase(status), genderValue, pageRequest);
        } else if (hasSearch) {
            customerPage = customerRepository.findAllBySearchKeyword("%" + search.trim().toLowerCase() + "%", pageRequest);
        } else if (hasStatus) {
            customerPage = customerRepository.findAllByIsActive("ACTIVE".equalsIgnoreCase(status), pageRequest);
        } else if (hasGender) {
            customerPage = customerRepository.findAllByGender(genderValue, pageRequest);
        } else {
            customerPage = customerRepository.findAll(pageRequest);
        }

        return PageResponse.<CustomerResponse>builder()
                .content(customerPage.getContent().stream()
                        .map(this::toCustomerResponse)
                        .toList())
                .page(customerPage.getNumber())
                .size(customerPage.getSize())
                .totalElements(customerPage.getTotalElements())
                .totalPages(customerPage.getTotalPages())
                .first(customerPage.isFirst())
                .last(customerPage.isLast())
                .build();
    }

    @Override
    @Transactional
    public CustomerResponse update(UUID id, CustomerRequest request) {
        Customer customer = findById(id);

        if (request.getPhone() != null && !request.getPhone().equals(customer.getPhone())) {
            customerRepository.findByPhone(request.getPhone())
                    .ifPresent(existing -> {
                        if (!existing.getId().equals(id)) {
                            throw new AppException(ErrorCode.CONFLICT, "Số điện thoại đã được sử dụng");
                        }
                    });
        }

        if (request.getEmail() != null && !request.getEmail().equals(customer.getEmail())) {
            customerRepository.findByEmail(request.getEmail())
                    .ifPresent(existing -> {
                        if (!existing.getId().equals(id)) {
                            throw new AppException(ErrorCode.CONFLICT, "Email đã được sử dụng");
                        }
                    });
        }

        customerMapper.updateEntityFromRequest(request, customer);
        Customer updatedCustomer = customerRepository.save(customer);
        return toCustomerResponse(updatedCustomer);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        if (!customerRepository.existsById(id)) {
            throw new AppException(ErrorCode.CUSTOMER_NOT_FOUND);
        }
        customerRepository.deleteById(id);
    }

    private Customer findById(UUID id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CUSTOMER_NOT_FOUND));
    }

    private CustomerResponse toCustomerResponse(Customer customer) {
        CustomerResponse response = customerMapper.toResponse(customer);
        UUID customerId = customer.getId();
        Long orderCount = orderRepository.countByCustomerId(customerId);
        BigDecimal totalSpent = orderRepository.sumTotalSpentByCustomerId(customerId);
        response.setOrderCount(orderCount);
        response.setTotalSpent(totalSpent != null ? totalSpent : BigDecimal.ZERO);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerStatsResponse getStats() {
        return CustomerStatsResponse.builder()
                .totalCustomers(customerRepository.countAll())
                .activeCustomers(customerRepository.countActive())
                .totalOrders(customerRepository.countAllOrders())
                .totalSpent(customerRepository.sumTotalSpent())
                .build();
    }
}
