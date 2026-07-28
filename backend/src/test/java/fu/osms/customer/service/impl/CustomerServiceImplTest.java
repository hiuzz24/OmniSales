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
import fu.osms.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CustomerServiceImpl Tests")
class CustomerServiceImplTest {

    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private CustomerMapper customerMapper;
    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private CustomerServiceImpl customerService;

    private UUID customerId;
    private Customer customer;
    private CustomerRequest customerRequest;
    private CustomerResponse customerResponse;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();

        customer = Customer.builder()
                .id(customerId)
                .fullName("Test Customer")
                .email("test@example.com")
                .phone("0912345678")
                .gender("Nam")
                .isActive(true)
                .build();
        customer.setCreatedAt(OffsetDateTime.now());
        customer.setUpdatedAt(OffsetDateTime.now());

        customerRequest = CustomerRequest.builder()
                .fullName("New Customer")
                .email("new@example.com")
                .phone("0987654321")
                .gender("Nam")
                .build();

        customerResponse = CustomerResponse.builder()
                .id(customerId)
                .fullName("Test Customer")
                .email("test@example.com")
                .phone("0912345678")
                .gender("Nam")
                .isActive(true)
                .orderCount(5L)
                .totalSpent(new BigDecimal("1500000"))
                .build();
    }

    // =========================================================
    // create() Tests
    // =========================================================
    @Nested
    @DisplayName("create() Tests")
    class CreateTests {

        @Test
        @DisplayName("Should create customer successfully")
        void shouldCreateCustomerSuccessfully() {
            when(customerRepository.findByPhone(customerRequest.getPhone())).thenReturn(Optional.empty());
            when(customerRepository.findByEmail(customerRequest.getEmail())).thenReturn(Optional.empty());
            when(customerMapper.toEntity(customerRequest)).thenReturn(customer);
            when(customerRepository.save(any(Customer.class))).thenReturn(customer);
            when(customerMapper.toResponse(customer)).thenReturn(customerResponse);
            when(orderRepository.countByCustomerId(customerId)).thenReturn(5L);
            when(orderRepository.sumTotalSpentByCustomerId(customerId)).thenReturn(new BigDecimal("1500000"));

            CustomerResponse result = customerService.create(customerRequest);

            assertThat(result).isNotNull();
            assertThat(result.getFullName()).isEqualTo("Test Customer");
            verify(customerRepository).save(any(Customer.class));
        }

        @Test
        @DisplayName("Should throw exception when phone already exists")
        void shouldThrowWhenPhoneExists() {
            when(customerRepository.findByPhone(customerRequest.getPhone())).thenReturn(Optional.of(customer));

            assertThatThrownBy(() -> customerService.create(customerRequest))
                    .isInstanceOf(AppException.class)
                    .satisfies(e -> assertThat(((AppException) e).getErrorCode())
                            .isEqualTo(ErrorCode.CONFLICT))
                    .hasMessageContaining("Số điện thoại đã được sử dụng");
        }

        @Test
        @DisplayName("Should throw exception when email already exists")
        void shouldThrowWhenEmailExists() {
            when(customerRepository.findByPhone(customerRequest.getPhone())).thenReturn(Optional.empty());
            when(customerRepository.findByEmail(customerRequest.getEmail())).thenReturn(Optional.of(customer));

            assertThatThrownBy(() -> customerService.create(customerRequest))
                    .isInstanceOf(AppException.class)
                    .satisfies(e -> assertThat(((AppException) e).getErrorCode())
                            .isEqualTo(ErrorCode.CONFLICT))
                    .hasMessageContaining("Email đã được sử dụng");
        }

        @Test
        @DisplayName("Should set isActive to true when not provided")
        void shouldSetDefaultIsActiveToTrue() {
            customerRequest.setIsActive(null);
            when(customerRepository.findByPhone(customerRequest.getPhone())).thenReturn(Optional.empty());
            when(customerRepository.findByEmail(customerRequest.getEmail())).thenReturn(Optional.empty());
            when(customerMapper.toEntity(customerRequest)).thenReturn(customer);
            when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> {
                Customer saved = invocation.getArgument(0);
                assertThat(saved.getIsActive()).isTrue();
                return saved;
            });
            when(customerMapper.toResponse(any(Customer.class))).thenReturn(customerResponse);
            when(orderRepository.countByCustomerId(any())).thenReturn(0L);
            when(orderRepository.sumTotalSpentByCustomerId(any())).thenReturn(null);

            customerService.create(customerRequest);

            verify(customerRepository).save(any(Customer.class));
        }
    }

    // =========================================================
    // getById() Tests
    // =========================================================
    @Nested
    @DisplayName("getById() Tests")
    class GetByIdTests {

        @Test
        @DisplayName("Should get customer by id with order count and total spent")
        void shouldGetCustomerByIdWithStats() {
            when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
            when(customerMapper.toResponse(customer)).thenReturn(customerResponse);
            when(orderRepository.countByCustomerId(customerId)).thenReturn(5L);
            when(orderRepository.sumTotalSpentByCustomerId(customerId)).thenReturn(new BigDecimal("1500000"));

            CustomerResponse result = customerService.getById(customerId);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(customerId);
            assertThat(result.getFullName()).isEqualTo("Test Customer");
            assertThat(result.getOrderCount()).isEqualTo(5L);
            assertThat(result.getTotalSpent()).isEqualTo(new BigDecimal("1500000"));
        }

        @Test
        @DisplayName("Should throw exception when customer not found")
        void shouldThrowWhenCustomerNotFound() {
            when(customerRepository.findById(customerId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> customerService.getById(customerId))
                    .isInstanceOf(AppException.class)
                    .satisfies(e -> assertThat(((AppException) e).getErrorCode())
                            .isEqualTo(ErrorCode.CUSTOMER_NOT_FOUND));
        }

        @Test
        @DisplayName("Should return zero when total spent is null")
        void shouldReturnZeroWhenTotalSpentIsNull() {
            CustomerResponse responseWithoutSpent = CustomerResponse.builder()
                    .id(customerId)
                    .fullName("Test Customer")
                    .orderCount(0L)
                    .totalSpent(BigDecimal.ZERO)
                    .build();

            when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
            when(customerMapper.toResponse(customer)).thenReturn(responseWithoutSpent);
            when(orderRepository.countByCustomerId(customerId)).thenReturn(0L);
            when(orderRepository.sumTotalSpentByCustomerId(customerId)).thenReturn(null);

            CustomerResponse result = customerService.getById(customerId);

            assertThat(result.getTotalSpent()).isEqualTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("REGRESSION: customer detail totalSpent propagates repository value")
        void shouldPropagateRepositoryTotalSpentValue() {
            when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
            when(customerMapper.toResponse(customer)).thenReturn(customerResponse);
            when(orderRepository.countByCustomerId(customerId)).thenReturn(7L);
            when(orderRepository.sumTotalSpentByCustomerId(customerId))
                    .thenReturn(new BigDecimal("1800000"));

            CustomerResponse result = customerService.getById(customerId);

            assertThat(result.getOrderCount()).isEqualTo(7L);
            assertThat(result.getTotalSpent()).isEqualByComparingTo(new BigDecimal("1800000"));
        }

        @Test
        @DisplayName("INVARIANT: customer detail totalSpent is non-negative")
        void shouldReturnNonNegativeTotalSpent() {
            when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
            when(customerMapper.toResponse(customer)).thenReturn(customerResponse);
            when(orderRepository.countByCustomerId(customerId)).thenReturn(0L);
            when(orderRepository.sumTotalSpentByCustomerId(customerId)).thenReturn(BigDecimal.ZERO);

            CustomerResponse result = customerService.getById(customerId);

            assertThat(result.getTotalSpent()).isNotNull();
            assertThat(result.getTotalSpent().signum()).isGreaterThanOrEqualTo(0);
        }
    }

    // =========================================================
    // getAll() Tests
    // =========================================================
    @Nested
    @DisplayName("getAll() Tests")
    class GetAllTests {

        @Test
        @DisplayName("Should get all customers with pagination")
        void shouldGetAllCustomersWithPagination() {
            Page<Customer> customerPage = new PageImpl<>(List.of(customer), PageRequest.of(0, 10), 1);
            when(customerRepository.findAll(any(PageRequest.class))).thenReturn(customerPage);
            when(customerMapper.toResponse(customer)).thenReturn(customerResponse);

            PageResponse<CustomerResponse> result = customerService.getAll(0, 10, null, null, null);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should filter customers by search keyword")
        void shouldFilterCustomersBySearchKeyword() {
            Page<Customer> customerPage = new PageImpl<>(List.of(customer), PageRequest.of(0, 10), 1);
            when(customerRepository.findAllBySearchKeyword(anyString(), any(PageRequest.class))).thenReturn(customerPage);
            when(customerMapper.toResponse(customer)).thenReturn(customerResponse);

            PageResponse<CustomerResponse> result = customerService.getAll(0, 10, "test", null, null);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
            verify(customerRepository).findAllBySearchKeyword(anyString(), any(PageRequest.class));
        }

        @Test
        @DisplayName("Should filter customers by status")
        void shouldFilterCustomersByStatus() {
            Page<Customer> customerPage = new PageImpl<>(List.of(customer), PageRequest.of(0, 10), 1);
            when(customerRepository.findAllByIsActive(eq(true), any(PageRequest.class))).thenReturn(customerPage);
            when(customerMapper.toResponse(customer)).thenReturn(customerResponse);

            PageResponse<CustomerResponse> result = customerService.getAll(0, 10, null, "ACTIVE", null);

            assertThat(result).isNotNull();
            verify(customerRepository).findAllByIsActive(eq(true), any(PageRequest.class));
        }

        @Test
        @DisplayName("Should filter customers by gender")
        void shouldFilterCustomersByGender() {
            Page<Customer> customerPage = new PageImpl<>(List.of(customer), PageRequest.of(0, 10), 1);
            when(customerRepository.findAllByGender(eq("Nam"), any(PageRequest.class))).thenReturn(customerPage);
            when(customerMapper.toResponse(customer)).thenReturn(customerResponse);

            PageResponse<CustomerResponse> result = customerService.getAll(0, 10, null, null, "Nam");

            assertThat(result).isNotNull();
            verify(customerRepository).findAllByGender(eq("Nam"), any(PageRequest.class));
        }

        @Test
        @DisplayName("Should normalize gender enum to Vietnamese label")
        void shouldNormalizeGenderEnum() {
            Page<Customer> customerPage = new PageImpl<>(List.of(customer), PageRequest.of(0, 10), 1);
            when(customerRepository.findAllByGender(eq("Nam"), any(PageRequest.class))).thenReturn(customerPage);
            when(customerMapper.toResponse(customer)).thenReturn(customerResponse);

            customerService.getAll(0, 10, null, null, "MALE");

            verify(customerRepository).findAllByGender(eq("Nam"), any(PageRequest.class));
        }
    }

    // =========================================================
    // update() Tests
    // =========================================================
    @Nested
    @DisplayName("update() Tests")
    class UpdateTests {

        @Test
        @DisplayName("Should update customer successfully")
        void shouldUpdateCustomerSuccessfully() {
            when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
            when(customerRepository.findByPhone(customerRequest.getPhone())).thenReturn(Optional.empty());
            when(customerRepository.findByEmail(customerRequest.getEmail())).thenReturn(Optional.empty());
            doNothing().when(customerMapper).updateEntityFromRequest(customerRequest, customer);
            when(customerRepository.save(any(Customer.class))).thenReturn(customer);
            when(customerMapper.toResponse(customer)).thenReturn(customerResponse);
            when(orderRepository.countByCustomerId(customerId)).thenReturn(5L);
            when(orderRepository.sumTotalSpentByCustomerId(customerId)).thenReturn(new BigDecimal("1500000"));

            CustomerResponse result = customerService.update(customerId, customerRequest);

            assertThat(result).isNotNull();
            verify(customerRepository).save(any(Customer.class));
        }

        @Test
        @DisplayName("Should throw exception when phone conflicts with another customer")
        void shouldThrowWhenPhoneConflictsWithAnotherCustomer() {
            UUID otherCustomerId = UUID.randomUUID();
            Customer otherCustomer = Customer.builder()
                    .id(otherCustomerId)
                    .phone(customerRequest.getPhone())
                    .build();

            when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
            when(customerRepository.findByPhone(customerRequest.getPhone())).thenReturn(Optional.of(otherCustomer));

            assertThatThrownBy(() -> customerService.update(customerId, customerRequest))
                    .isInstanceOf(AppException.class)
                    .satisfies(e -> assertThat(((AppException) e).getErrorCode())
                            .isEqualTo(ErrorCode.CONFLICT))
                    .hasMessageContaining("Số điện thoại đã được sử dụng");
        }

        @Test
        @DisplayName("Should throw exception when email conflicts with another customer")
        void shouldThrowWhenEmailConflictsWithAnotherCustomer() {
            UUID otherCustomerId = UUID.randomUUID();
            Customer otherCustomer = Customer.builder()
                    .id(otherCustomerId)
                    .email(customerRequest.getEmail())
                    .build();

            when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
            when(customerRepository.findByPhone(customerRequest.getPhone())).thenReturn(Optional.empty());
            when(customerRepository.findByEmail(customerRequest.getEmail())).thenReturn(Optional.of(otherCustomer));

            assertThatThrownBy(() -> customerService.update(customerId, customerRequest))
                    .isInstanceOf(AppException.class)
                    .satisfies(e -> assertThat(((AppException) e).getErrorCode())
                            .isEqualTo(ErrorCode.CONFLICT))
                    .hasMessageContaining("Email đã được sử dụng");
        }

        @Test
        @DisplayName("Should allow update when phone is unchanged")
        void shouldAllowUpdateWhenPhoneUnchanged() {
            customerRequest.setPhone(customer.getPhone());

            when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
            doNothing().when(customerMapper).updateEntityFromRequest(customerRequest, customer);
            when(customerRepository.save(any(Customer.class))).thenReturn(customer);
            when(customerMapper.toResponse(customer)).thenReturn(customerResponse);
            when(orderRepository.countByCustomerId(customerId)).thenReturn(5L);
            when(orderRepository.sumTotalSpentByCustomerId(customerId)).thenReturn(new BigDecimal("1500000"));

            CustomerResponse result = customerService.update(customerId, customerRequest);

            assertThat(result).isNotNull();
            verify(customerRepository, never()).findByPhone(anyString());
        }
    }

    // =========================================================
    // delete() Tests
    // =========================================================
    @Nested
    @DisplayName("delete() Tests")
    class DeleteTests {

        @Test
        @DisplayName("Should delete customer successfully")
        void shouldDeleteCustomerSuccessfully() {
            when(customerRepository.existsById(customerId)).thenReturn(true);

            customerService.delete(customerId);

            verify(customerRepository).deleteById(customerId);
        }

        @Test
        @DisplayName("Should throw exception when customer not found for delete")
        void shouldThrowWhenCustomerNotFoundForDelete() {
            when(customerRepository.existsById(customerId)).thenReturn(false);

            assertThatThrownBy(() -> customerService.delete(customerId))
                    .isInstanceOf(AppException.class)
                    .satisfies(e -> assertThat(((AppException) e).getErrorCode())
                            .isEqualTo(ErrorCode.CUSTOMER_NOT_FOUND));
        }
    }

    // =========================================================
    // getStats() Tests
    // =========================================================
    @Nested
    @DisplayName("getStats() Tests")
    class GetStatsTests {

        @Test
        @DisplayName("Should get customer stats successfully")
        void shouldGetCustomerStatsSuccessfully() {
            when(customerRepository.countAll()).thenReturn(100L);
            when(customerRepository.countActive()).thenReturn(80L);
            when(customerRepository.countAllOrders()).thenReturn(500L);
            when(customerRepository.sumTotalSpent()).thenReturn(new BigDecimal("50000000"));

            CustomerStatsResponse result = customerService.getStats();

            assertThat(result).isNotNull();
            assertThat(result.getTotalCustomers()).isEqualTo(100L);
            assertThat(result.getActiveCustomers()).isEqualTo(80L);
            assertThat(result.getTotalOrders()).isEqualTo(500L);
            assertThat(result.getTotalSpent()).isEqualTo(new BigDecimal("50000000"));
        }

        @Test
        @DisplayName("Should handle null total spent in stats")
        void shouldHandleNullTotalSpentInStats() {
            when(customerRepository.countAll()).thenReturn(0L);
            when(customerRepository.countActive()).thenReturn(0L);
            when(customerRepository.countAllOrders()).thenReturn(0L);
            when(customerRepository.sumTotalSpent()).thenReturn(null);

            CustomerStatsResponse result = customerService.getStats();

            assertThat(result.getTotalSpent()).isEqualTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("REGRESSION: stats totalSpent propagates repository value")
        void shouldPropagateRepositoryTotalSpentValue() {
            when(customerRepository.countAll()).thenReturn(6L);
            when(customerRepository.countActive()).thenReturn(5L);
            when(customerRepository.countAllOrders()).thenReturn(20L);
            when(customerRepository.sumTotalSpent()).thenReturn(new BigDecimal("104171200"));

            CustomerStatsResponse result = customerService.getStats();

            assertThat(result.getTotalCustomers()).isEqualTo(6L);
            assertThat(result.getActiveCustomers()).isEqualTo(5L);
            assertThat(result.getTotalOrders()).isEqualTo(20L);
            assertThat(result.getTotalSpent()).isEqualByComparingTo(new BigDecimal("104171200"));
        }

        @Test
        @DisplayName("INVARIANT: stats totalSpent is non-negative even for empty data")
        void shouldReturnNonNegativeStatsTotalSpent() {
            when(customerRepository.countAll()).thenReturn(0L);
            when(customerRepository.countActive()).thenReturn(0L);
            when(customerRepository.countAllOrders()).thenReturn(0L);
            when(customerRepository.sumTotalSpent()).thenReturn(BigDecimal.ZERO);

            CustomerStatsResponse result = customerService.getStats();

            assertThat(result.getTotalSpent()).isNotNull();
            assertThat(result.getTotalSpent().signum()).isGreaterThanOrEqualTo(0);
        }
    }
}
