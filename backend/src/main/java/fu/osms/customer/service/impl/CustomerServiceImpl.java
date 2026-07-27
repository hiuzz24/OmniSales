package fu.osms.customer.service.impl;

import fu.osms.common.dto.PageResponse;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.customer.dto.request.CustomerRequest;
import fu.osms.customer.dto.response.CustomerResponse;
import fu.osms.customer.dto.response.CustomerStatsResponse;
import fu.osms.customer.dto.response.CustomerWithOrderSourceResponse;
import fu.osms.customer.dto.response.PageWithOrderCustomersResponse;
import fu.osms.customer.entity.Customer;
import fu.osms.customer.mapper.CustomerMapper;
import fu.osms.customer.repository.CustomerRepository;
import fu.osms.customer.service.CustomerService;
import fu.osms.order.entity.Order;
import fu.osms.order.repository.OrderRepository;
import fu.osms.order.repository.projection.CustomerOrderAggregate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
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
        BigDecimal totalSpent = customerRepository.sumTotalSpent();
        return CustomerStatsResponse.builder()
                .totalCustomers(customerRepository.countAll())
                .activeCustomers(customerRepository.countActive())
                .totalOrders(customerRepository.countAllOrders())
                .totalSpent(totalSpent != null ? totalSpent : BigDecimal.ZERO)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageWithOrderCustomersResponse getPageWithOrderCustomers(int page, int size, String search, String status, String gender) {
        // 1. Get filtered page of customers with their existing per-customer order stats
        PageResponse<CustomerResponse> filteredPage = getAll(page, size, search, status, gender);

        Set<UUID> pageIds = filteredPage.getContent().stream()
                .map(CustomerResponse::getId)
                .collect(Collectors.toSet());

        // 2. Find customer IDs that have orders (customer != null, not CANCELLED) but are NOT on the current page
        List<UUID> allOrderCustomerIds = orderRepository.findCustomerIdsWithNonNullCustomer();
        List<UUID> missingIds = allOrderCustomerIds.stream()
                .filter(id -> !pageIds.contains(id))
                .toList();

        List<CustomerWithOrderSourceResponse> merged = new ArrayList<>(
                filteredPage.getContent().size() + missingIds.size());

        // 3. Existing customers: fromOrders = false
        for (CustomerResponse r : filteredPage.getContent()) {
            merged.add(CustomerWithOrderSourceResponse.from(r, false));
        }

        // 4. Aggregate order stats for missing customers in one bulk query
        if (!missingIds.isEmpty()) {
            Map<UUID, CustomerOrderAggregate> aggMap = orderRepository.aggregateByCustomerIds(missingIds).stream()
                    .collect(Collectors.toMap(CustomerOrderAggregate::getCustomerId, a -> a));

            List<UUID> idsWithOrders = missingIds.stream()
                    .filter(aggMap::containsKey)
                    .toList();

            if (!idsWithOrders.isEmpty()) {
                Map<UUID, Customer> customerMap = customerRepository.findAllById(idsWithOrders).stream()
                        .collect(Collectors.toMap(Customer::getId, c -> c));

                for (UUID cid : idsWithOrders) {
                    Customer c = customerMap.get(cid);
                    if (c == null) continue; // customer was deleted but orders remain
                    CustomerOrderAggregate agg = aggMap.get(cid);
                    CustomerResponse base = toCustomerResponse(c);
                    base.setOrderCount(agg.getOrderCount());
                    base.setTotalSpent(agg.getTotalSpent());
                    merged.add(CustomerWithOrderSourceResponse.from(base, true));
                }
            }
        }

        // 5. Aggregate totals over the merged list
        long totalOrders = merged.stream().mapToLong(r -> Optional.ofNullable(r.getOrderCount()).orElse(0L)).sum();
        BigDecimal totalSpent = merged.stream()
                .map(r -> Optional.ofNullable(r.getTotalSpent()).orElse(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        PageWithOrderCustomersResponse.AggregatedStats aggregated = PageWithOrderCustomersResponse.AggregatedStats.builder()
                .totalOrders(totalOrders)
                .totalSpent(totalSpent)
                .build();

        return PageWithOrderCustomersResponse.builder()
                .content(merged)
                .page(filteredPage.getPage())
                .size(filteredPage.getSize())
                .totalElements(filteredPage.getTotalElements())
                .totalPages(filteredPage.getTotalPages())
                .first(filteredPage.isFirst())
                .last(filteredPage.isLast())
                .aggregated(aggregated)
                .build();
    }

    @Override
    @Transactional
    public Customer findOrCreateFromBuyer(String buyerName, String buyerPhone) {
        // Normalize: null/blank inputs -> defaults
        String safeName = (buyerName == null || buyerName.isBlank()) ? "Khách vãng lai" : buyerName.trim();
        String safePhone = (buyerPhone == null || buyerPhone.isBlank()) ? null : buyerPhone.trim();

        // Try dedupe by fullName + phone first
        Optional<Customer> existing = customerRepository.findFirstByFullNameAndPhone(safeName, safePhone);
        if (existing.isPresent()) {
            return existing.get();
        }

        // Fall back to phone-only match (some orders have empty buyerName but buyerPhone)
        if (safePhone != null) {
            existing = customerRepository.findByPhone(safePhone);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        Customer customer = Customer.builder()
                .fullName(safeName)
                .phone(safePhone)
                .isActive(true)
                .build();
        return customerRepository.save(customer);
    }

    @Override
    @Transactional
    public int syncCustomersFromOrders() {
        long nullCount = orderRepository.countByCustomerIsNull();
        if (nullCount == 0) {
            log.info("[syncCustomersFromOrders] No orders with null customer; skip");
            return 0;
        }

        List<Order> nullOrders = orderRepository.findAllByCustomerIsNullOrderByCreatedAtAsc();
        log.info("[syncCustomersFromOrders] Backfilling {} orders with null customer", nullOrders.size());

        int updated = 0;
        Map<String, Customer> customerCache = new HashMap<>();

        for (Order order : nullOrders) {
            String buyerName = order.getBuyerName();
            String buyerPhone = order.getBuyerPhone();
            String safeName = (buyerName == null || buyerName.isBlank()) ? "Khách vãng lai" : buyerName.trim();
            String safePhone = (buyerPhone == null || buyerPhone.isBlank()) ? null : buyerPhone.trim();
            String cacheKey = safeName + "||" + safePhone;

            Customer customer = customerCache.get(cacheKey);
            if (customer == null) {
                customer = findOrCreateFromBuyer(safeName, safePhone);
                customerCache.put(cacheKey, customer);
            }

            order.setCustomer(customer);
            updated++;
        }

        orderRepository.saveAll(nullOrders);
        log.info("[syncCustomersFromOrders] Done. Updated {} orders", updated);
        return updated;
    }
}
