package fu.osms.inventory.controller;

import fu.osms.auth.repository.UserRepository;
import fu.osms.auth.security.JwtAuthenticationFilter;
import fu.osms.auth.security.JwtService;
import fu.osms.auth.service.AuthService;
import fu.osms.auth.service.UserService;
import fu.osms.common.dto.PageResponse;
import fu.osms.config.ApiUsageFilter;
import fu.osms.inventory.dto.request.StockReceiveRequest;
import fu.osms.inventory.dto.response.StockReceiveResponse;
import fu.osms.inventory.service.StockReceiveService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for {@link StockReceiveController}.
 *
 * <p>Role-based authorization (@PreAuthorize) is verified separately in
 * the full E2E backend tests where the real security filter chain is
 * applied; here we focus on controller-level happy paths and validation.</p>
 */
@WebMvcTest(
        controllers = StockReceiveController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, ApiUsageFilter.class}
        )
)
@AutoConfigureMockMvc(addFilters = false)
class StockReceiveControllerIT {

    @Autowired MockMvc mvc;
    @MockBean StockReceiveService stockReceiveService;
    @MockBean JwtService jwtService;
    @MockBean UserRepository userRepository;
    @MockBean UserService userService;
    @MockBean AuthService authService;
    @MockBean org.springframework.security.core.userdetails.UserDetailsService userDetailsService;

    private StockReceiveResponse sampleReceipt(UUID id) {
        return StockReceiveResponse.builder()
                .id(id)
                .warehouseId(UUID.randomUUID())
                .warehouseName("Main")
                .receiptCode("RC-0001")
                .status("DRAFT")
                .totalCost(BigDecimal.valueOf(500000))
                .receivedAt(java.time.OffsetDateTime.now())
                .build();
    }

    @Test
    void getById_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(stockReceiveService.getReceiptById(id)).thenReturn(sampleReceipt(id));

        mvc.perform(get("/api/receipts/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(id.toString()))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    void getReceipts_returnsPage() throws Exception {
        UUID id = UUID.randomUUID();
        PageResponse<StockReceiveResponse> page = PageResponse.<StockReceiveResponse>builder()
                .content(List.of(sampleReceipt(id)))
                .page(0).size(20).totalElements(1).totalPages(1).first(true).last(true)
                .build();
        when(stockReceiveService.getReceipts(eq(0), eq(20))).thenReturn(page);

        mvc.perform(get("/api/receipts?page=0&size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void getNextCode_returns200() throws Exception {
        when(stockReceiveService.getNextReceiptCode()).thenReturn("RC-0001");
        mvc.perform(get("/api/receipts/next-code"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("RC-0001"));
    }

    @Test
    void create_returns400_whenItemsEmpty() throws Exception {
        // @NotEmpty on items -> 400
        UUID whId = UUID.randomUUID();
        String body = """
                {
                  "warehouseId": "%s",
                  "receivedAt": "%s",
                  "items": []
                }
                """.formatted(whId, LocalDate.now());
        mvc.perform(post("/api/receipts")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}