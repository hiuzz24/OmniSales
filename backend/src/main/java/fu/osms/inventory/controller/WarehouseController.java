package fu.osms.inventory.controller;

import fu.osms.common.dto.ApiResponse;
import fu.osms.inventory.dto.request.WarehouseRequest;
import fu.osms.inventory.dto.response.WarehouseResponse;
import fu.osms.inventory.service.WarehouseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/warehouses")
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseService warehouseService;

    @PostMapping
    public ResponseEntity<ApiResponse<WarehouseResponse>> create(@Valid @RequestBody WarehouseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Warehouse created successfully", warehouseService.create(request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<WarehouseResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.getById(id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<WarehouseResponse>>> getByShop(@RequestParam UUID shopId) {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.getByShopId(shopId)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<WarehouseResponse>> update(@PathVariable UUID id,
                                                                  @Valid @RequestBody WarehouseRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Warehouse updated successfully", warehouseService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        warehouseService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Warehouse deleted successfully", null));
    }
}
