package fu.osms.customer.controller;

import fu.osms.auth.repository.UserRepository;
import fu.osms.auth.security.JwtAuthenticationFilter;
import fu.osms.auth.security.JwtService;
import fu.osms.auth.service.AuthService;
import fu.osms.auth.service.UserService;
import fu.osms.config.ApiUsageFilter;
import fu.osms.customer.dto.request.CustomerRequest;
import fu.osms.customer.dto.response.CustomerResponse;
import fu.osms.customer.service.CustomerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for {@link CustomerController}.
 *
 * <p>{@code addFilters = false} keeps the focus on controller behavior;
 * security-related tests (403 for unauthorized roles, JWT validation,
 * login flow) live in the full E2E backend tests using the real
 * security filter chain.</p>
 */
@WebMvcTest(
        controllers = CustomerController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, ApiUsageFilter.class}
        )
)
@AutoConfigureMockMvc(addFilters = false)
class CustomerControllerIT {

    @Autowired MockMvc mvc;
    @MockitoBean CustomerService customerService;
    // Mock all beans that the auto-discovered filters would otherwise require
    @MockitoBean JwtService jwtService;
    @MockitoBean UserRepository userRepository;
    @MockitoBean UserService userService;
    @MockitoBean AuthService authService;
    @MockitoBean org.springframework.security.core.userdetails.UserDetailsService userDetailsService;

    @Test
    void getById_returns200_andCustomerEnvelope() throws Exception {
        UUID id = UUID.randomUUID();
        CustomerResponse resp = CustomerResponse.builder()
                .id(id)
                .code("KH000001")
                .fullName("Nguyen Van A")
                .email("a@example.com")
                .phone("0901234567")
                .isActive(true)
                .orderCount(3L)
                .totalSpent(BigDecimal.valueOf(1500000))
                .build();
        when(customerService.getById(id)).thenReturn(resp);

        mvc.perform(get("/api/customers/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(id.toString()))
                .andExpect(jsonPath("$.data.fullName").value("Nguyen Van A"))
                .andExpect(jsonPath("$.data.code").value("KH000001"));
    }

    @Test
    void getAll_returnsPageResponse_envelope() throws Exception {
        CustomerResponse c1 = CustomerResponse.builder().id(UUID.randomUUID()).fullName("A").isActive(true).build();
        CustomerResponse c2 = CustomerResponse.builder().id(UUID.randomUUID()).fullName("B").isActive(true).build();
        var page = fu.osms.common.dto.PageResponse.<CustomerResponse>builder()
                .content(List.of(c1, c2))
                .page(0).size(20).totalElements(2).totalPages(1).first(true).last(true)
                .build();
        when(customerService.getAll(eq(0), eq(20), eq(null), eq(null), eq(null))).thenReturn(page);

        mvc.perform(get("/api/customers?page=0&size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void create_returns201_withCreatedCustomer() throws Exception {
        CustomerResponse resp = CustomerResponse.builder()
                .id(UUID.randomUUID())
                .code("KH000099")
                .fullName("New Customer")
                .email("new@example.com")
                .isActive(true)
                .orderCount(0L)
                .totalSpent(BigDecimal.ZERO)
                .build();
        when(customerService.create(any(CustomerRequest.class))).thenReturn(resp);

        mvc.perform(post("/api/customers")
                        .contentType("application/json")
                        .content("{\"fullName\":\"New Customer\",\"email\":\"new@example.com\",\"phone\":\"0901234567\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.fullName").value("New Customer"));
    }

    @Test
    void create_returns400_whenEmailInvalid() throws Exception {
        mvc.perform(post("/api/customers")
                        .contentType("application/json")
                        .content("{\"fullName\":\"Bad\",\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void delete_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(delete("/api/customers/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getStats_returns200() throws Exception {
        var stats = fu.osms.customer.dto.response.CustomerStatsResponse.builder()
                .totalCustomers(10L)
                .activeCustomers(8L)
                .totalOrders(25L)
                .totalSpent(BigDecimal.valueOf(5000000))
                .build();
        when(customerService.getStats()).thenReturn(stats);

        mvc.perform(get("/api/customers/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCustomers").value(10))
                .andExpect(jsonPath("$.data.activeCustomers").value(8));
    }
}