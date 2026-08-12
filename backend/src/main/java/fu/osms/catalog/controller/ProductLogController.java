package fu.osms.catalog.controller;

import fu.osms.catalog.dto.response.ProductLogResponse;
import fu.osms.catalog.service.ProductLogService;
import fu.osms.common.dto.ApiResponse;
import fu.osms.common.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/product-logs")
@RequiredArgsConstructor
public class ProductLogController {

    private final ProductLogService productLogService;

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'SALES')")
    public ResponseEntity<ApiResponse<PageResponse<ProductLogResponse>>> getLogs(
            @RequestParam(required = false) UUID productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "performedAt,desc") String sort
    ) {
        PageResponse<ProductLogResponse> response = productLogService.getLogs(productId, page, size, sort);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
