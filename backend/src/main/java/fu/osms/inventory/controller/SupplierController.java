package fu.osms.inventory.controller;

import fu.osms.common.dto.ApiResponse;
import fu.osms.common.dto.PageResponse;
import fu.osms.inventory.dto.request.SupplierRequest;
import fu.osms.inventory.dto.response.SupplierResponse;
import fu.osms.inventory.service.SupplierService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

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

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER', 'OPERATIONS')")
    public ResponseEntity<ApiResponse<SupplierResponse>> create(
            @Valid @RequestBody SupplierRequest request) {

        SupplierResponse response = supplierService.create(request);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','OPERATIONS')")
    public ResponseEntity<ApiResponse<SupplierResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody SupplierRequest request) {

        SupplierResponse response = supplierService.update(id, request);

        return ResponseEntity.ok(ApiResponse.success(response));
    }


    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('OWNER','OPERATIONS')")
    public ResponseEntity<ApiResponse<SupplierResponse>> updateStatus(
            @PathVariable UUID id,
            @RequestBody Map<String, Boolean> body) {

        SupplierResponse response = supplierService.updateStatus(id, body.get("isActive"));

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
