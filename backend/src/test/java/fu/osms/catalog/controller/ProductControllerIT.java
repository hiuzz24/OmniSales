package fu.osms.catalog.controller;

import fu.osms.auth.repository.UserRepository;
import fu.osms.auth.security.JwtAuthenticationFilter;
import fu.osms.auth.security.JwtService;
import fu.osms.auth.service.AuthService;
import fu.osms.auth.service.UserService;
import fu.osms.catalog.dto.request.ProductRequest;
import fu.osms.catalog.dto.response.ProductResponse;
import fu.osms.catalog.service.ProductImportService;
import fu.osms.catalog.service.ProductService;
import fu.osms.common.dto.PageResponse;
import fu.osms.config.ApiUsageFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for {@link ProductController}.
 */
@WebMvcTest(
        controllers = ProductController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, ApiUsageFilter.class}
        )
)
@AutoConfigureMockMvc(addFilters = false)
class ProductControllerIT {

    @Autowired MockMvc mvc;
    @MockBean ProductService productService;
    @MockBean ProductImportService productImportService;
    @MockBean JwtService jwtService;
    @MockBean UserRepository userRepository;
    @MockBean UserService userService;
    @MockBean AuthService authService;
    @MockBean org.springframework.security.core.userdetails.UserDetailsService userDetailsService;

    private ProductResponse sampleProduct(UUID id) {
        return ProductResponse.builder()
                .id(id)
                .sku("SKU-001")
                .name("Test Product")
                .status(fu.osms.catalog.enums.ProductStatus.ACTIVE)
                .build();
    }

    @Test
    void getById_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(productService.getById(id)).thenReturn(sampleProduct(id));

        mvc.perform(get("/api/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id.toString()))
                .andExpect(jsonPath("$.data.sku").value("SKU-001"));
    }

    @Test
    void search_returnsPage() throws Exception {
        UUID id = UUID.randomUUID();
        PageResponse<ProductResponse> page = PageResponse.<ProductResponse>builder()
                .content(List.of(sampleProduct(id)))
                .page(0).size(6).totalElements(1).totalPages(1).first(true).last(true)
                .build();
        when(productService.search(eq("test"), eq(null), eq(null), eq(0), eq(6))).thenReturn(page);

        mvc.perform(get("/api/products?keyword=test&page=0&size=6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void create_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(productService.create(any(ProductRequest.class))).thenReturn(sampleProduct(id));

        String body = """
                {
                  "sku": "SKU-NEW",
                  "name": "New Product",
                  "variants": [{"sku": "VAR-1", "name": "Default"}],
                  "categoryId": "%s"
                }
                """.formatted(UUID.randomUUID());

        mvc.perform(post("/api/products")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").exists());
    }

    @Test
    void delete_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(delete("/api/products/{id}/delete", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}