package fu.osms.inventory.controller;

import fu.osms.common.dto.ApiResponse;
import fu.osms.inventory.dto.request.WarehouseRequest;
import fu.osms.inventory.dto.response.WarehouseResponse;
import fu.osms.inventory.service.WarehouseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/warehouses")
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseService warehouseService;

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<WarehouseResponse>> create(@Valid @RequestBody WarehouseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(warehouseService.create(request)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'OPERATIONS', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<WarehouseResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.getById(id)));
    }

    @GetMapping("/master")
    @PreAuthorize("hasAnyRole('OWNER', 'OPERATIONS')")
    public ResponseEntity<ApiResponse<WarehouseResponse>> getMaster() {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.getMaster()));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'OPERATIONS')")
    public ResponseEntity<ApiResponse<List<WarehouseResponse>>> getAll(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        List<WarehouseResponse> warehouses = warehouseService.getAll(keyword, status);
        return ResponseEntity.ok(ApiResponse.success(warehouses));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<WarehouseResponse>> update(@PathVariable UUID id,
                                                                 @Valid @RequestBody WarehouseRequest request) {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.update(id, request)));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('OWNER', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<WarehouseResponse>> toggleStatus(@PathVariable UUID id,
                                                                       @RequestBody java.util.Map<String, Boolean> body) {
        Boolean isActive = body.getOrDefault("isActive", true);
        return ResponseEntity.ok(ApiResponse.success(warehouseService.toggleStatus(id, isActive)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        warehouseService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/userWarehouse/{id}")
    public ResponseEntity<ApiResponse<WarehouseResponse>> getUserWarehouseById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.getWarehouseByUserId(id)));
    }
}
