package fu.osms.catalog.controller;

import fu.osms.catalog.dto.response.ProductVariantResponse;
import fu.osms.catalog.service.ProductVariantService;
import fu.osms.common.dto.ApiResponse;
import fu.osms.common.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog/variants")
@RequiredArgsConstructor
public class ProductVariantController {

    private final ProductVariantService productVariantService;

    /**
     * GET /api/catalog/variants?search={keyword}&page=0&size=20
     * Tìm kiếm product variant theo SKU hoặc tên sản phẩm.
     * Chỉ trả về variant có is_active = true và deleted_at IS NULL.
     * Yêu cầu role OWNER hoặc OPERATIONS.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'OPERATIONS')")
    public ResponseEntity<ApiResponse<PageResponse<ProductVariantResponse>>> search(
            @RequestParam(name = "search", required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageResponse<ProductVariantResponse> result = productVariantService.search(keyword, page, size);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
