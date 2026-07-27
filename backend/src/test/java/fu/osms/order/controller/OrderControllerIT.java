package fu.osms.order.controller;

import fu.osms.auth.repository.UserRepository;
import fu.osms.auth.security.JwtAuthenticationFilter;
import fu.osms.auth.security.JwtService;
import fu.osms.auth.service.AuthService;
import fu.osms.auth.service.UserService;
import fu.osms.channel.service.ChannelService;
import fu.osms.common.dto.PageResponse;
import fu.osms.common.enums.PlatformType;
import fu.osms.config.ApiUsageFilter;
import fu.osms.order.dto.request.OrderRequest;
import fu.osms.order.dto.response.OrderResponse;
import fu.osms.order.enums.OrderStatus;
import fu.osms.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for {@link OrderController}.
 */
@WebMvcTest(
        controllers = OrderController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, ApiUsageFilter.class}
        )
)
@AutoConfigureMockMvc(addFilters = false)
class OrderControllerIT {

    @Autowired MockMvc mvc;
    @MockBean OrderService orderService;
    @MockBean ChannelService channelService;
    @MockBean JwtService jwtService;
    @MockBean UserRepository userRepository;
    @MockBean UserService userService;
    @MockBean AuthService authService;
    @MockBean org.springframework.security.core.userdetails.UserDetailsService userDetailsService;

    private OrderResponse sampleOrder(UUID id) {
        return OrderResponse.builder()
                .id(id)
                .platform(PlatformType.MANUAL)
                .channelName("Manual")
                .externalOrderId("EXT-001")
                .status(OrderStatus.PENDING)
                .paymentStatus("UNPAID")
                .buyerName("Buyer")
                .shippingAddress(Map.of("city", "HCMC"))
                .subtotal(BigDecimal.valueOf(100000))
                .totalAmount(BigDecimal.valueOf(100000))
                .currency("VND")
                .items(List.of())
                .build();
    }

    @Test
    void getById_returns200_envelope() throws Exception {
        UUID id = UUID.randomUUID();
        when(orderService.getById(id)).thenReturn(sampleOrder(id));

        mvc.perform(get("/api/orders/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(id.toString()))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void getAll_returnsPage() throws Exception {
        UUID id = UUID.randomUUID();
        PageResponse<OrderResponse> page = PageResponse.<OrderResponse>builder()
                .content(List.of(sampleOrder(id)))
                .page(0).size(20).totalElements(1).totalPages(1).first(true).last(true)
                .build();
        when(orderService.getFiltered(eq(null), eq(null), eq(null), eq(null), eq(null), eq(0), eq(20)))
                .thenReturn(page);

        mvc.perform(get("/api/orders?page=0&size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void getStats_returns200() throws Exception {
        when(orderService.getStats()).thenReturn(
                new fu.osms.order.dto.response.OrderStats(
                        100L, 5L, 10L, 0L, 5L, 80L, 15L, BigDecimal.valueOf(50000000)));

        mvc.perform(get("/api/orders/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalOrders").value(100))
                .andExpect(jsonPath("$.data.deliveredCount").value(80));
    }

    @Test
    void updateStatus_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        OrderResponse resp = sampleOrder(id);
        resp.setStatus(OrderStatus.CONFIRMED);
        when(orderService.updateStatus(eq(id), eq(OrderStatus.CONFIRMED))).thenReturn(resp);

        mvc.perform(patch("/api/orders/{id}/status", id)
                        .param("status", "CONFIRMED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
    }

    @Test
    void cancel_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(orderService).cancel(eq(id), any());

        mvc.perform(post("/api/orders/{id}/cancel", id)
                        .param("reason", "out of stock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void create_returns201() throws Exception {
        UUID id = UUID.randomUUID();
        when(orderService.create(any(OrderRequest.class))).thenReturn(sampleOrder(id));

        String body = """
                {
                  "platform": "MANUAL",
                  "channelName": "Manual",
                  "externalOrderId": "EXT-001",
                  "buyerName": "Buyer",
                  "shippingAddress": {"city": "HCMC"},
                  "subtotal": 100000,
                  "items": [{"name": "Item 1", "quantity": 1, "unitPrice": 100000}]
                }
                """;
        mvc.perform(post("/api/orders")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").exists());
    }

    @Test
    void create_returns400_whenMissingRequiredFields() throws Exception {
        // No externalOrderId, no items -> fails @NotBlank and @NotEmpty
        mvc.perform(post("/api/orders")
                        .contentType("application/json")
                        .content("{\"platform\":\"MANUAL\",\"channelName\":\"X\"}"))
                .andExpect(status().isBadRequest());
    }

    // =========================================================
    // Additional Tests - Extended Coverage
    // =========================================================

    @Test
    void getOrders_withFilters_returnsFilteredResults() throws Exception {
        UUID id = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        PageResponse<OrderResponse> page = PageResponse.<OrderResponse>builder()
                .content(List.of(sampleOrder(id)))
                .page(0).size(20).totalElements(1).totalPages(1).first(true).last(true)
                .build();
        when(orderService.getFiltered(
                eq(OrderStatus.PENDING),
                eq(channelId),
                eq("test keyword"),
                any(), any(),
                eq(0),
                eq(20)))
                .thenReturn(page);

        mvc.perform(get("/api/orders")
                        .param("status", "PENDING")
                        .param("channelId", channelId.toString())
                        .param("keyword", "test keyword")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void getOrders_withDateFilters_returnsFilteredResults() throws Exception {
        UUID id = UUID.randomUUID();
        PageResponse<OrderResponse> page = PageResponse.<OrderResponse>builder()
                .content(List.of(sampleOrder(id)))
                .page(0).size(20).totalElements(1).totalPages(1).first(true).last(true)
                .build();
        when(orderService.getFiltered(
                eq(null), eq(null), eq(null),
                any(), any(),
                eq(0), eq(20)))
                .thenReturn(page);

        mvc.perform(get("/api/orders")
                        .param("from", "2026-01-01")
                        .param("to", "2026-12-31")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    void update_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(orderService.update(eq(id), any(OrderRequest.class))).thenReturn(sampleOrder(id));

        String body = """
                {
                  "platform": "MANUAL",
                  "channelName": "Manual",
                  "externalOrderId": "EXT-001",
                  "buyerName": "Updated Buyer",
                  "shippingAddress": {"city": "Hanoi"},
                  "subtotal": 200000,
                  "items": [{"name": "Item 1", "quantity": 2, "unitPrice": 100000}]
                }
                """;
        mvc.perform(put("/api/orders/{id}", id)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void update_returns400_whenInvalidRequest() throws Exception {
        UUID id = UUID.randomUUID();

        mvc.perform(put("/api/orders/{id}", id)
                        .contentType("application/json")
                        .content("{\"platform\":\"MANUAL\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updatePaymentStatus_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        OrderResponse resp = sampleOrder(id);
        resp.setPaymentStatus("PAID");
        when(orderService.updatePaymentStatus(eq(id), any())).thenReturn(resp);

        mvc.perform(patch("/api/orders/{id}/payment-status", id)
                        .param("paymentStatus", "PAID"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("PAID"));
    }

    @Test
    void cancel_withRequestBody_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(orderService).cancel(eq(id), any());

        String body = """
                {
                  "reason": "Customer requested cancellation"
                }
                """;
        mvc.perform(post("/api/orders/{id}/cancel", id)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getOrderHistory_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        PageResponse<fu.osms.audit.dto.response.AuditLogResponse> historyPage =
                PageResponse.<fu.osms.audit.dto.response.AuditLogResponse>builder()
                        .content(List.of())
                        .page(0).size(20).totalElements(0).totalPages(0).first(true).last(true)
                        .build();
        when(orderService.getOrderHistory(eq(id), eq(0), eq(20))).thenReturn(historyPage);

        mvc.perform(get("/api/orders/{id}/history", id)
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    void getCancelReasons_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        List<fu.osms.order.dto.response.CancelReasonResponse> reasons = List.of(
                fu.osms.order.dto.response.CancelReasonResponse.builder()
                        .id("OUT_OF_STOCK")
                        .name("Hết hàng")
                        .build(),
                fu.osms.order.dto.response.CancelReasonResponse.builder()
                        .id("CUSTOMER_REQUEST")
                        .name("Khách hàng yêu cầu")
                        .build()
        );
        when(orderService.getCancelReasons(id)).thenReturn(reasons);

        mvc.perform(get("/api/orders/{id}/cancel-reasons", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));
    }
}