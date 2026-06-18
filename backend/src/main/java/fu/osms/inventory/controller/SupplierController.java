package fu.osms.inventory.controller;

import fu.osms.common.dto.ApiResponse;
import fu.osms.common.dto.PageResponse;
import fu.osms.inventory.dto.response.SupplierResponse;
import fu.osms.inventory.service.SupplierService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService supplierService;

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'OPERATIONS')")
    public ResponseEntity<ApiResponse<PageResponse<SupplierResponse>>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResponse<SupplierResponse> suppliers = supplierService.getAll(page, size);
        return ResponseEntity.ok(ApiResponse.success(suppliers));
    }
}
